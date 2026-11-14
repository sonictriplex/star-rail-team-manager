"""Tests für Build-Daten, Relikt-Panel, Set-Zusammenfassung, Grenzen und das Austauschformat (Desktop).

Start (im Ordner desktop/):  python -m unittest discover -s tests -v
Die Oberfläche läuft ohne Bildschirm (QT_QPA_PLATFORM=offscreen), die Datenbank ist eine Wegwerf-Datei.
"""
import json
import os
import sys
import tempfile
import unittest
import warnings

warnings.simplefilter("ignore", ResourceWarning)
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from PySide6.QtWidgets import QApplication

import widgets
from manager import EXCHANGE_FORMAT, DatabaseError, StarRailManager
from models import RELIC_SLOT_NAMES, TALENT_MAX, active_set_bonuses, normalize_relics
from styles import APP_QSS

app = None


def setUpModule():
    global app
    app = QApplication.instance() or QApplication([])
    app.setStyleSheet(APP_QSS)


class BuildDataTest(unittest.TestCase):
    def setUp(self):
        warnings.simplefilter("ignore", ResourceWarning)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.manager = StarRailManager(os.path.join(self.tmp.name, "test.db"))

    def test_stammdaten_geladen(self):
        self.assertEqual(self.manager.load_errors, [])
        self.assertGreater(len(self.manager.available_characters), 50)
        self.assertGreater(len(self.manager.available_light_cones), 100)
        self.assertTrue(self.manager.relic_sets_of_kind("Relikt"))
        self.assertTrue(self.manager.relic_sets_of_kind("Ornament"))

    def test_build_rundreise_mit_grenzen(self):
        lc = self.manager.available_light_cones[0].name
        self.manager.save_user_character("Kafka", {
            "level": 999, "eidolon": 9, "light_cone": lc, "lc_level": 0, "lc_superimpose": 9,
            "talents": {"basic": 50, "skill": 11, "ult": 0, "talent": 12},
            "relics": {"Kopf": {"set": "X", "level": 99, "rarity": 9}, "Unbekannt": {"set": "Y"}},
        })
        b = self.manager.load_user_characters()["Kafka"]
        self.assertEqual((b["level"], b["eidolon"], b["lc_level"], b["lc_superimpose"]), (80, 6, 1, 5))
        self.assertEqual(b["talents"], {"basic": TALENT_MAX["basic"], "skill": 11, "ult": 1, "talent": 12})
        self.assertEqual(list(b["relics"]), ["Kopf"])                       # unbekannte Slots werden verworfen
        self.assertEqual((b["relics"]["Kopf"]["level"], b["relics"]["Kopf"]["rarity"]), (15, 5))
        self.assertEqual(len(b["relics"]["Kopf"]["substats"]), 4)

    def test_set_bonus(self):
        relics = {s: {"set": "A"} for s in RELIC_SLOT_NAMES[:4]}
        relics["Planarsphäre"] = {"set": "O"}
        relics["Verbindungsseil"] = {"set": "O"}
        self.assertEqual(active_set_bonuses(relics), [("A", 4), ("O", 2)])
        relics["Füße"] = {"set": "B"}
        self.assertEqual(active_set_bonuses(relics), [("A", 2), ("O", 2)])   # 3 Teile gelten als 2er-Bonus
        self.assertEqual(active_set_bonuses({}), [])
        self.assertEqual(normalize_relics({"Kopf": {"set": "— Kein Relikt-Set —"}})["Kopf"]["set"], "")

    def test_export_import_und_webformat(self):
        self.manager.save_user_character("Kafka", {"level": 70, "talents": {"skill": 8}})
        self.manager.saved_teams = {"Team": ["Kafka", "Acheron"]}
        self.manager.save_teams()
        path = os.path.join(self.tmp.name, "export.json")
        self.manager.export_user_data(path)
        data = json.load(open(path, encoding="utf-8"))
        self.assertEqual((data["format"], data["version"]), (EXCHANGE_FORMAT, 1))
        self.assertEqual(set(data["builds"][0]), {"name", "level", "eidolon", "light_cone", "lc_level",
                                                  "lc_superimpose", "relics", "talents"})   # gleiche Felder wie die Web-API
        other = StarRailManager(os.path.join(self.tmp.name, "other.db"))
        result = other.import_user_data(path)
        self.assertEqual((result["builds"], result["teams"], result["skipped"]), (1, 1, []))
        self.assertEqual(other.load_user_characters()["Kafka"]["level"], 70)
        # Datei eines anderen Managers wird abgelehnt
        json.dump({"format": "genshin-team-manager", "version": 1}, open(path, "w"))
        with self.assertRaises(DatabaseError):
            other.import_user_data(path)


class RelicPanelTest(unittest.TestCase):
    def setUp(self):
        warnings.simplefilter("ignore", ResourceWarning)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.manager = StarRailManager(os.path.join(self.tmp.name, "test.db"))
        self.card = widgets.CharacterCardWidget(self.manager.get_character_by_name("Kafka"), self.manager, {})
        self.panel = widgets.RelicPanel(self.card, self.manager)

    def test_standard_ist_unbearbeitet(self):
        self.assertEqual(widgets.normalize_build({"relics": self.panel.get_relics()})["relics"], {})
        self.assertEqual(list(self.panel.get_relics()), RELIC_SLOT_NAMES)

    def test_set_auswahl_getrennt_nach_art(self):
        kopf, sphere = self.panel.slots["Kopf"], self.panel.slots["Planarsphäre"]
        names = lambda combo: {combo.itemData(i) for i in range(combo.count())} - {""}
        self.assertEqual(names(kopf.relic_set), {r.name for r in self.manager.relic_sets_of_kind("Relikt")})
        self.assertEqual(names(sphere.relic_set), {r.name for r in self.manager.relic_sets_of_kind("Ornament")})

    def test_zusammenfassung_folgt_den_slots(self):
        relic = self.manager.relic_sets_of_kind("Relikt")[0].name
        for slot in RELIC_SLOT_NAMES[:4]:
            w = self.panel.slots[slot]
            w.relic_set.setCurrentIndex(w.relic_set.findData(relic))
        self.assertIn("4×", self.card.set_summary.text())
        self.assertIn(relic.replace("&", "&amp;"), self.card.set_summary.text())
        self.panel.reset()
        self.assertIn("Keine aktiven", self.card.set_summary.text())

    def test_talentfelder_haben_grenzen(self):
        for key, spin in self.card.talent_spins.items():
            self.assertEqual(spin.maximum(), TALENT_MAX[key])



class TooltipTest(unittest.TestCase):
    def setUp(self):
        warnings.simplefilter("ignore", ResourceWarning)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.manager = StarRailManager(os.path.join(self.tmp.name, "test.db"))

    def test_lichtkegel_haben_faehigkeiten(self):
        cones = self.manager.available_light_cones
        self.assertTrue(all(lc.passive and len(lc.passive["text"]) == 5 for lc in cones))
        lc = self.manager.get_light_cone_by_name("Along the Passing Shore")
        self.assertEqual(lc.passive["name"], "Fährmann")                      # deutsche Texte
        self.assertIn("36%", lc.passive_tooltip(1))
        self.assertIn("60%", lc.passive_tooltip(5))                           # Überlagerung wirkt auf die Werte
        self.assertIn("Überlagerung 5", lc.passive_tooltip(9))                # außerhalb 1..5 wird begrenzt

    def test_karte_zeigt_faehigkeit_passend_zur_ueberlagerung(self):
        lc = self.manager.get_light_cone_by_name("Along the Passing Shore")
        card = widgets.CharacterCardWidget(self.manager.get_character_by_name("Kafka"), self.manager,
                                           {"light_cone": lc.name, "lc_superimpose": 1})
        self.assertIn("36%", card.lc_input.toolTip())
        card.superimpose_row.buttons[5].click()
        self.assertIn("60%", card.lc_input.toolTip())
        card.set_light_cone("")
        self.assertEqual(card.lc_input.toolTip(), "")

    def test_setboni_sind_deutsch(self):
        sets = self.manager.available_relic_sets
        self.assertTrue(all(r.bonus_2pc for r in sets))
        self.assertIn("Erhöht", self.manager.get_relic_set_by_name("Amphoreus, The Eternal Land").bonus_2pc)

    def test_bestehende_datenbank_wird_nachgeruestet(self):
        import sqlite3
        path = os.path.join(self.tmp.name, "alt.db")
        with sqlite3.connect(path) as conn:           # Datenbank ohne die Spalte passive_json
            conn.executescript("""CREATE TABLE light_cones (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL,
                path TEXT NOT NULL, rarity INTEGER NOT NULL, icon_path TEXT);
                INSERT INTO light_cones (name, path, rarity) VALUES ('Along the Passing Shore', 'Die Nichtigkeit', 5);""")
        manager = StarRailManager(path)
        self.assertEqual(manager.load_errors, [])
        self.assertEqual(manager.get_light_cone_by_name("Along the Passing Shore").passive["name"], "Fährmann")


if __name__ == "__main__":
    unittest.main()
