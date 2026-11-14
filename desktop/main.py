"""Startpunkt der Anwendung: python main.py"""
import sys

from PySide6.QtWidgets import QApplication

from main_window import MainWindow
from styles import APP_QSS


def main():
    app = QApplication(sys.argv)
    # Eigene Fensterklasse (Wayland app_id), damit generische KWin-Regeln für "python" nicht greifen.
    # Der Desktop-Dateiname muss dem Namen des Starters entsprechen (star-rail-manager.desktop), sonst kann
    # die Taskleiste das Fenster nicht dem Starter und seinem Icon zuordnen.
    app.setApplicationName("star-rail-team-manager")
    app.setDesktopFileName("star-rail-manager")
    app.setStyleSheet(APP_QSS)
    window = MainWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
