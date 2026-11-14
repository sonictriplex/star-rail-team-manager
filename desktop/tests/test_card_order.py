"""Tests für das Verschieben der Teamkarten (Pfeile und Drag & Drop) .

Start (im Ordner desktop/):  python -m unittest discover -s tests -v

Die Oberfläche läuft ohne Bildschirm (QT_QPA_PLATFORM=offscreen), die Datenbank ist eine Wegwerf-Datei.
Das Ziehen mit der Maus selbst lässt sich so nicht auslösen: Der Test prüft, dass ein Drag am Kartenkopf
startet (QDrag ist durch eine Attrappe ersetzt) und dass das Ablegen die richtige Position trifft.
"""
import os
import sqlite3
import sys
import tempfile
import unittest
import warnings
from unittest import mock

# manager.py schließt seine SQLite-Verbindungen nicht ausdrücklich; das ist für die Tests ohne Belang
warnings.simplefilter("ignore", ResourceWarning)

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from PySide6.QtCore import QMimeData, QPoint, QPointF, Qt
from PySide6.QtGui import QDropEvent
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication, QMessageBox

import widgets
from manager import StarRailManager
from main_window import MainWindow
from styles import APP_QSS

app = None


def setUpModule():
    global app
    app = QApplication.instance() or QApplication([])
    app.setStyleSheet(APP_QSS)


class CardOrderTest(unittest.TestCase):
    NAMES = ("Kafka", "Acheron", "Ruan Mei")

    def setUp(self):
        warnings.simplefilter("ignore", ResourceWarning)   # gilt nur innerhalb des Tests (unittest setzt die Filter pro Test)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.manager = StarRailManager(os.path.join(self.tmp.name, "test.db"))
        # Modale Fenster würden den Test anhalten (auch die Rückfrage beim Schließen mit ungespeicherten Änderungen)
        for name, answer in (("information", QMessageBox.Ok), ("warning", QMessageBox.Ok),
                             ("critical", QMessageBox.Ok), ("question", QMessageBox.Yes)):
            patcher = mock.patch.object(QMessageBox, name, staticmethod(lambda *a, _answer=answer, **k: _answer))
            patcher.start()
            self.addCleanup(patcher.stop)

        self.window = MainWindow(self.manager)
        self.window.show()
        team = [{"character": self.manager.get_character_by_name(n), "saved": {"level": 70 + i}}
                for i, n in enumerate(self.NAMES)]
        self.window.render_team_cards(team)
        self.addCleanup(self.window.close)
        app.processEvents()

    # --- Hilfen ---

    def order(self):
        return [c.character.name for c in self.window.current_team_cards]

    def levels(self):
        return {c.character.name: c.get_card_data()["level"] for c in self.window.current_team_cards}

    def drop(self, name, x):
        mime = QMimeData()
        mime.setData(widgets.CARD_MIME, name.encode("utf-8"))
        event = QDropEvent(QPointF(x, 100), Qt.MoveAction, mime, Qt.LeftButton, Qt.NoModifier)
        self.window.team_overview_widget.dropEvent(event)
        app.processEvents()
        return event.isAccepted()

    def card_x(self, index):
        return self.window.current_team_cards[index].geometry().center().x()

    # --- Pfeile ---

    def test_startreihenfolge_und_pfeile_an_den_raendern(self):
        cards = self.window.current_team_cards
        self.assertEqual(self.order(), ["Kafka", "Acheron", "Ruan Mei"])
        self.assertFalse(cards[0].move_left_btn.isEnabled())
        self.assertTrue(cards[0].move_right_btn.isEnabled())
        self.assertFalse(cards[2].move_right_btn.isEnabled())

    def test_pfeile_verschieben_und_eingaben_wandern_mit(self):
        self.window.current_team_cards[0].move_right_btn.click()
        self.assertEqual(self.order(), ["Acheron", "Kafka", "Ruan Mei"])
        self.window.current_team_cards[2].move_left_btn.click()
        self.assertEqual(self.order(), ["Acheron", "Ruan Mei", "Kafka"])
        self.assertEqual(self.levels(), {"Acheron": 71, "Ruan Mei": 72, "Kafka": 70})

    # --- Drag starten ---

    def test_drag_startet_nur_am_kartenkopf(self):
        started = []

        class FakeDrag:
            def __init__(self, source): pass
            def setMimeData(self, mime): started.append(bytes(mime.data(widgets.CARD_MIME).data()).decode())
            def setPixmap(self, pixmap): pass
            def setHotSpot(self, point): pass
            def exec(self, action): return action

        card = self.window.current_team_cards[0]
        with mock.patch.object(widgets, "QDrag", FakeDrag):
            QTest.mousePress(card, Qt.LeftButton, Qt.NoModifier, QPoint(60, 60))     # Kopfbereich
            QTest.mouseMove(card, QPoint(200, 70))
            self.assertEqual(started, ["Kafka"])

            started.clear()
            QTest.mousePress(card, Qt.LeftButton, Qt.NoModifier, QPoint(60, 300))    # Formularbereich
            QTest.mouseMove(card, QPoint(200, 310))
            QTest.mouseRelease(card, Qt.LeftButton, Qt.NoModifier, QPoint(200, 310))
            self.assertEqual(started, [])

    # --- Ablegen ---

    def test_zielindex(self):
        area = self.window.team_overview_widget
        self.assertEqual(area.target_index("Kafka", 1), 0)
        self.assertEqual(area.target_index("Acheron", area.width() - 1), 2)

    def test_drop_ans_ende_an_den_anfang_und_in_die_mitte(self):
        self.drop("Kafka", self.card_x(2) + 20)
        self.assertEqual(self.order(), ["Acheron", "Ruan Mei", "Kafka"])
        self.drop("Kafka", 1)
        self.assertEqual(self.order(), ["Kafka", "Acheron", "Ruan Mei"])
        self.drop("Ruan Mei", self.card_x(0) + 5)
        self.assertEqual(self.order(), ["Kafka", "Ruan Mei", "Acheron"])

    def test_drop_auf_sich_selbst_und_fremde_daten_aendern_nichts(self):
        self.drop("Acheron", self.card_x(1))
        self.assertEqual(self.order(), ["Kafka", "Acheron", "Ruan Mei"])

        mime = QMimeData()
        mime.setText("irgendwas")
        event = QDropEvent(QPointF(10, 10), Qt.CopyAction, mime, Qt.LeftButton, Qt.NoModifier)
        self.window.team_overview_widget.dropEvent(event)
        self.assertFalse(event.isAccepted())
        self.assertEqual(self.order(), ["Kafka", "Acheron", "Ruan Mei"])

    def test_nur_karten_im_layout_zaehlen(self):
        # Direkt nach dem Neuaufbau warten entfernte Karten noch auf deleteLater(); sie dürfen nicht mitzählen
        area = self.window.team_overview_widget
        self.window.move_card(0, 2)
        self.window.move_card(0, 2)
        self.assertEqual(area.target_index("Kafka", area.width() - 1), 2)

    # --- Speichern ---

    def test_reihenfolge_wird_gespeichert_und_gilt_als_aenderung(self):
        self.window.team_name_input.setText("Reihenfolge")
        self.window.save_current_team()
        app.processEvents()
        self.assertFalse(self.window.has_unsaved_changes())

        self.window.move_card(0, 2)
        self.assertTrue(self.window.has_unsaved_changes())

        self.window.save_current_team()
        app.processEvents()
        saved = [m.get("name") if isinstance(m, dict) else m for m in self.manager.saved_teams["Reihenfolge"]]
        self.assertEqual(saved, ["Acheron", "Ruan Mei", "Kafka"])
        self.assertFalse(self.window.has_unsaved_changes())


if __name__ == "__main__":
    unittest.main()
