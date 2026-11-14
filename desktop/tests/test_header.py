"""Tests für die Werkzeugleiste (Kopfzeile) des Hauptfensters.

Start (im Ordner desktop/):  python -m unittest discover -s tests -v
"""
import os
import sys
import tempfile
import unittest
import warnings
from unittest import mock

warnings.simplefilter("ignore", ResourceWarning)
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from PySide6.QtWidgets import QApplication, QLabel, QMessageBox, QPushButton

from manager import StarRailManager
from main_window import MainWindow
from styles import APP_QSS

app = None


def setUpModule():
    global app
    app = QApplication.instance() or QApplication([])
    app.setStyleSheet(APP_QSS)


class HeaderTest(unittest.TestCase):
    def setUp(self):
        warnings.simplefilter("ignore", ResourceWarning)   # gilt nur innerhalb des Tests (unittest setzt die Filter pro Test)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.manager = StarRailManager(os.path.join(self.tmp.name, "test.db"))
        self.answer = QMessageBox.Yes      # Antwort auf Rückfragen; pro Test änderbar
        for name, result in (("information", QMessageBox.Ok), ("warning", QMessageBox.Ok), ("critical", QMessageBox.Ok)):
            patcher = mock.patch.object(QMessageBox, name, staticmethod(lambda *a, _r=result, **k: _r))
            patcher.start()
            self.addCleanup(patcher.stop)
        patcher = mock.patch.object(QMessageBox, "question", staticmethod(lambda *a, **k: self.answer))
        patcher.start()
        self.addCleanup(patcher.stop)

        self.window = MainWindow(self.manager)
        self.window.show()
        self.addCleanup(self.window.close)
        app.processEvents()

    def button(self, text):
        return next(b for b in self.window.findChildren(QPushButton) if b.text() == text)

    def menu_action(self, text):
        file_menu = next(a.menu() for a in self.window.menuBar().actions() if a.text() == "Datei")
        return next(a for a in file_menu.actions() if a.text() == text)

    def delete_button(self):
        return next(b for b in self.window.findChildren(QPushButton) if b.accessibleName() == "Team löschen")

    def save_team(self, name, members=("Kafka",)):
        team = [{"character": self.manager.get_character_by_name(n), "saved": {}} for n in members]
        self.window.render_team_cards(team)
        self.window.team_name_input.setText(name)
        self.window.save_current_team()
        app.processEvents()

    def test_vorhandene_bedienelemente_mit_kurzen_beschriftungen(self):
        self.assertEqual(self.window.team_name_input.placeholderText(), "Teamname")
        self.button("Speichern")
        self.assertEqual(self.window.team_name_input.text(), "Neues Team")

    def test_loeschen_ist_ein_symbolknopf_mit_tooltip(self):
        delete = self.delete_button()
        self.assertEqual(delete.text(), "")
        self.assertFalse(delete.icon().isNull(), "das Symbol muss geladen werden")
        self.assertEqual(delete.toolTip(), "Team löschen")
        self.assertEqual(delete.objectName(), "dangerButton")

    def test_felder_passen_sich_der_breite_an(self):
        for width in (1080, 1500):
            self.window.resize(width, 640)
            app.processEvents()
            name_width = self.window.team_name_input.width()
            combo_width = self.window.teams_combo.width()
            self.assertGreaterEqual(name_width, 140)
            self.assertLessEqual(name_width, 260)
            self.assertGreaterEqual(combo_width, 160)
            self.assertLessEqual(combo_width, 320)

    def test_loeschen_fragt_nach_und_loescht_das_ausgewaehlte_team(self):
        self.save_team("Zu loeschen")
        self.assertIn("Zu loeschen", self.manager.saved_teams)
        self.window.teams_combo.setCurrentText("Zu loeschen")

        self.answer = QMessageBox.No
        self.delete_button().click()
        self.assertIn("Zu loeschen", self.manager.saved_teams, "bei Nein bleibt das Team")

        self.answer = QMessageBox.Yes
        self.delete_button().click()
        self.assertNotIn("Zu loeschen", self.manager.saved_teams)

    def test_alle_teams_zeigt_eine_karte_je_team_und_oeffnet_das_angeklickte(self):
        from widgets import TeamSummaryCard
        self.save_team("Erstes", ("Kafka",))
        self.window.new_team()      # sonst fragt das Speichern unter anderem Namen nach dem Umbenennen
        self.save_team("Zweites", ("Kafka", "Acheron"))
        self.menu_action("Alle Teams").trigger()
        app.processEvents()
        self.assertIs(self.window.stack.currentWidget(), self.window.all_teams_scroll)
        cards = self.window.all_teams_content.findChildren(TeamSummaryCard)
        self.assertEqual(sorted(c.team_name for c in cards), ["Erstes", "Zweites"])

        next(c for c in cards if c.team_name == "Zweites").clicked.emit("Zweites")
        app.processEvents()
        self.assertIs(self.window.stack.currentWidget(), self.window.team_overview_widget)
        self.assertEqual(self.window.team_name_input.text(), "Zweites")
        self.assertEqual(self.window.teams_combo.currentText(), "Zweites")
        self.assertEqual(len(self.window.current_team_cards), 2)

    def test_alle_teams_ohne_teams_zeigt_hinweis(self):
        self.menu_action("Alle Teams").trigger()
        self.assertIs(self.window.stack.currentWidget(), self.window.all_teams_scroll)

    def test_start_mit_vorhandenen_teams_zeigt_alle_teams(self):
        self.save_team("Startteam")
        window = MainWindow(self.manager)      # neues Fenster mit denselben Daten, wie ein Neustart
        self.addCleanup(window.close)
        self.assertIs(window.stack.currentWidget(), window.all_teams_scroll)

    def test_datei_menue_hat_export_und_import(self):
        menus = {a.text(): a.menu() for a in self.window.menuBar().actions()}
        self.assertIn("Datei", menus)
        self.assertEqual([a.text() for a in menus["Datei"].actions()], ["Alle Teams", "Neues Team", "", "Exportieren…", "Importieren…"])

    def save_teams(self, *names):
        """Legt mehrere Teams nacheinander an (zwischendurch "Neues Team", sonst fragt das Speichern nach dem Umbenennen)."""
        for name in names:
            self.window.new_team()
            self.save_team(name)

    def overview_names(self):
        grid = self.window.all_teams_grid
        cards = [grid.itemAt(i).widget() for i in range(grid.count())]
        return [c.team_name for c in cards if c is not None and hasattr(c, "team_name")]

    def test_teamuebersicht_pfeile_verschieben_und_speichern_die_reihenfolge(self):
        self.save_teams("Alpha", "Beta", "Gamma")
        self.window.show_all_teams()
        app.processEvents()
        self.assertEqual(self.overview_names(), ["Alpha", "Beta", "Gamma"])
        self.window.on_team_move_requested("Alpha", 1)
        app.processEvents()
        self.assertEqual(self.overview_names(), ["Beta", "Alpha", "Gamma"])
        # Dropdown und Datenbank folgen der neuen Reihenfolge
        self.assertEqual([self.window.teams_combo.itemText(i) for i in range(1, 4)], ["Beta", "Alpha", "Gamma"])
        self.manager.load_teams()
        self.assertEqual(list(self.manager.saved_teams), ["Beta", "Alpha", "Gamma"])

    def test_teamuebersicht_drop_verschiebt_das_team_und_neue_teams_kommen_ans_ende(self):
        self.save_teams("Alpha", "Beta", "Gamma")
        self.window.on_team_dropped("Gamma", "Alpha")
        self.assertEqual(self.overview_names(), ["Gamma", "Alpha", "Beta"])
        self.save_teams("Delta")
        self.manager.load_teams()
        self.assertEqual(list(self.manager.saved_teams), ["Gamma", "Alpha", "Beta", "Delta"])

    def test_teamuebersicht_pfeile_an_den_raendern_sind_deaktiviert(self):
        self.save_teams("Alpha", "Beta")
        self.window.show_all_teams()
        grid = self.window.all_teams_grid
        first, last = grid.itemAt(0).widget(), grid.itemAt(1).widget()
        buttons = lambda card: card.findChildren(QPushButton)
        self.assertFalse(buttons(first)[0].isEnabled())
        self.assertTrue(buttons(first)[1].isEnabled())
        self.assertFalse(buttons(last)[1].isEnabled())


if __name__ == "__main__":
    unittest.main()


class AboutTest(HeaderTest):
    def test_about_menu_opens_dialog_with_version_matching_changelog(self):
        import re
        from dialogs import AboutDialog
        from models import APP_VERSION
        changelog = open(os.path.join(os.path.dirname(__file__), "..", "..", "CHANGELOG.md"), encoding="utf-8").read()
        self.assertEqual(APP_VERSION, re.search(r"^## Desktop (\d+\.\d+\.\d+)", changelog, re.M).group(1))

        help_menu = next(a.menu() for a in self.window.menuBar().actions() if a.text() == "Hilfe")
        about = next(a for a in help_menu.actions() if a.text() == "Über…")
        opened = []
        with mock.patch.object(AboutDialog, "exec", lambda dialog: opened.append(dialog)):
            about.trigger()
        self.assertEqual(len(opened), 1)
        self.assertIn(APP_VERSION, opened[0].version_label.text())
        texts = " ".join(label.text() for label in opened[0].findChildren(QLabel))
        self.assertIn("HoYoverse", texts)
        self.assertTrue(any(l.objectName() == "aboutLogo" and l.pixmap() and not l.pixmap().isNull() for l in opened[0].findChildren(QLabel)),
                        "das media-matrix-Logo wird angezeigt")

    def test_about_logo_click_opens_media_matrix_website(self):
        from PySide6.QtCore import QPoint, Qt
        from PySide6.QtTest import QTest
        import dialogs
        dialog = dialogs.AboutDialog(self.window)
        dialog.show()
        app.processEvents()
        logo = dialog.findChild(QLabel, "aboutLogo")
        with mock.patch.object(dialogs.QDesktopServices, "openUrl", return_value=True) as open_url:
            QTest.mouseClick(logo, Qt.LeftButton, Qt.NoModifier, QPoint(logo.width() // 2, logo.height() // 2))
        self.assertEqual(open_url.call_count, 1)
        self.assertEqual(open_url.call_args[0][0].toString(), "https://www.media-matrix.de")
        dialog.close()
