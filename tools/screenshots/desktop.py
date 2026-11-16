#!/usr/bin/env python3
"""Desktop-Screenshots nach docs/screenshots/desktop/ (PySide6 offscreen, temporäre Datenbank).

Aufruf:  python3 tools/screenshots/desktop.py   (Voraussetzung: PySide6, pillow nicht nötig)"""
import os, sys, json, tempfile
os.environ["QT_QPA_PLATFORM"] = "offscreen"
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from demo_data import ROOT, export_file
OUT = ROOT / "docs/screenshots/desktop"
sys.path.insert(0, str(ROOT / "desktop"))
from PySide6.QtWidgets import QApplication, QMessageBox
from manager import StarRailManager
from main_window import MainWindow
from dialogs import CharacterSearchDialog, LightConeSearchDialog, AboutDialog
from styles import APP_QSS
app = QApplication([]); app.setStyleSheet(APP_QSS)
tmp = tempfile.mkdtemp()
exp = export_file()
p = os.path.join(tmp, "demo.json"); json.dump(exp, open(p, "w"), ensure_ascii=False)
mgr = StarRailManager(os.path.join(tmp, "demo.db")); print(mgr.import_user_data(p))
w = MainWindow(mgr); w.resize(1100, 700); w.show(); app.processEvents()
def grab(widget, name):
    app.processEvents(); widget.grab().save(str(OUT / name), "JPG", 90)
w.show_all_teams(); app.processEvents(); grab(w, "all-teams.jpg")
w.teams_combo.setCurrentText("Blitz-Team"); app.processEvents(); grab(w, "overview.jpg")
card = w.current_team_cards[0]; card.toggle_stats_btn.click(); app.processEvents(); grab(w, "build.jpg")
card.toggle_stats_btn.click(); app.processEvents()
d = CharacterSearchDialog(mgr.available_characters, w); d.show(); d.search_input.setText("a") if hasattr(d, "search_input") else None; grab(d, "character-search.jpg"); d.close()
d = LightConeSearchDialog(mgr.available_light_cones, "Die Nichtigkeit", w); d.show(); grab(d, "lightcone-search.jpg"); d.close()
d = AboutDialog(w); d.show(); grab(d, "about.jpg"); d.close()
