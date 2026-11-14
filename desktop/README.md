# Honkai: Star Rail – Team & Build Manager (Desktop)

Desktop-App zum Zusammenstellen von Star-Rail-Teams und zum Verwalten der Charakter-Builds. Gebaut mit Python, PySide6 (Qt) und SQLite. Die Daten liegen lokal, ein Konto braucht es nicht.

## Funktionen

- **Teams** mit bis zu 4 Charakteren anlegen, speichern, laden, umbenennen und löschen; die Reihenfolge der Karten ändert man per Drag & Drop am Kopfbereich oder mit den Pfeilen ‹ ›, die der Teams in der Übersicht ebenso
- **Builds pro Charakter:** Level 1–80, Eidolon E0–E6, Talente (Basis bis 7, Fertigkeit / Ultimate / Talent bis 12), Lichtkegel mit Level und S1–S5
- **Relikt-Details:** sechs Slots (Kopf, Hände, Körper, Füße, Planarsphäre, Verbindungsseil) mit Set (Relikt-Sets bzw. Planar-Ornamente getrennt), Seltenheit, Level, Hauptwert und vier Substats; die aktiven 2er-/4er-Boni berechnet die Karte selbst
- **Suche und Filter** für Charaktere (Pfad, Element, Seltenheit) und Lichtkegel (Pfad, Seltenheit); der Pfad des Charakters ist in der Lichtkegel-Suche vorausgewählt
- **Tooltips** mit den Boni der Relikt-Sets
- **Export und Import** im Menü „Datei“, im selben Format wie die Web-Version (`star-rail-team-manager`, Version 1): Builds und Teams lassen sich zwischen Desktop und Web austauschen

Ein Build gilt für den Charakter, nicht für das Team: Wird ein Charakter in mehreren Teams verwendet, hat er überall denselben Build.

## Start

```bash
cd desktop
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

Voraussetzungen: Python 3.10 oder neuer, PySide6 6.12 oder neuer. Die App lässt sich aus jedem Verzeichnis starten; Datenbank (`desktop/star_rail_manager.db`) und Bilder (`shared/`) werden relativ zum Repo gefunden.

## Datenbank und Stammdaten

Alle Daten liegen in `star_rail_manager.db`. Die Datei ist **nicht im Git-Repository**, weil sie persönliche Teams und Builds enthält und separat gesichert werden sollte. Die Stammdaten (Charaktere, Lichtkegel, Relikt-Sets) stehen versioniert in `shared/stammdaten.json` und werden beim ersten Start eingespielt. Neue Einträge holt man mit `python tools/update_stammdaten.py` (im Repo-Hauptordner) in diese Datei; mit `python seed.py import` landen sie in einer vorhandenen Datenbank.

## Tests

```bash
cd desktop
python -m unittest discover -s tests -v
```

Die Oberfläche läuft dabei ohne Bildschirm (`QT_QPA_PLATFORM=offscreen`) mit einer Wegwerf-Datenbank.
