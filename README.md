# Honkai: Star Rail – Team & Build Manager

Teams aus bis zu vier Charakteren zusammenstellen und für jeden Charakter einen Build verwalten: Level, Eidolon, Talente, Lichtkegel (Level, Überlagerung) und alle sechs Relikte mit Set, Hauptwert und Substats. Gleiches Konzept wie der Genshin-Manager, zunächst als **Web-App** (PHP + MySQL/MariaDB). Desktop und Android sind noch nicht portiert.

## Funktionen

- Teams mit bis zu 4 Charakteren anlegen, speichern, umbenennen, löschen und per Pfeile oder Drag & Drop sortieren
- Build pro Charakter: Level 1–80, Eidolon E0–E6, Talente (Basis, Fertigkeit, Ultimate, Talent), Lichtkegel mit Level und S1–S5
- Sechs Relikt-Slots (Kopf, Hände, Körper, Füße, Planarsphäre, Verbindungsseil) mit Set (Relikte bzw. Planar-Ornamente getrennt), Seltenheit, Level, Hauptwert und vier Substats; aktive 2er/4er-Boni stehen auf der Karte
- Suche und Filter für Charaktere (Pfad, Element, Seltenheit) und Lichtkegel (Pfad, Seltenheit)
- Export und Import aller Builds und Teams als JSON, Registrierung, Login, Konto löschen

Ein Build gehört zum **Charakter, nicht zum Team**: Kommt ein Charakter in mehreren Teams vor, hat er überall denselben Build.

## Einrichtung

1. Webserver mit PHP und PDO-MySQL; der Inhalt von `web/` gehört ins Web-Root (Symlink `assets` auflösen: `rsync -rL web/ …`).
2. Datenbank anlegen, dann `web/schema.sql` und `web/seed_stammdaten.sql` einspielen.
3. `cp web/api/config.example.php web/api/config.local.php` und Zugangsdaten eintragen (oder `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASS`).
4. Lokal testen: `cd web && php -S localhost:8000`.

## Stammdaten aktualisieren

`shared/stammdaten.json` enthält Charaktere, Lichtkegel und Relikt-Sets (Quelle: [Mar-7th/StarRailRes](https://github.com/Mar-7th/StarRailRes), Stand der Erstellung 14.11.2026). Nach Änderungen `python3 web/tools/make_seed.py` ausführen und die SQL-Datei erneut einspielen. Neue Grafiken kommen nach `shared/assets/images`.

<sub>Inoffizielles Fanprojekt, nicht mit HoYoverse verbunden. Honkai: Star Rail sowie zugehörige Namen und Bilder sind Eigentum von HoYoverse.</sub>
