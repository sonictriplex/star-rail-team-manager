# Änderungsverlauf

Dieser Verlauf fasst die wichtigsten Änderungen zusammen. Web, Desktop und Android sind getrennte Clients mit eigenen Versionsnummern; die Web-Version läuft per `deploy/deploy.sh` auf dem Server, der Desktop direkt aus dem Repo.

## Android 1.2.0 (14.11.2026)
- Tooltips wie in der Genshin-App: Die Fähigkeit des Lichtkegels lässt sich in der Build-Ansicht und beim Bearbeiten über „ⓘ Beschreibung anzeigen“ aufklappen und folgt der gewählten Überlagerung (S1–S5); in der Lichtkegel-Auswahl steht sie unter dem Namen. Die Set-Boni der Relikte sind jetzt auf Deutsch

## Android 1.1.0 (14.11.2026)
- „Passwort ändern“ im Menü (⋮): aktuelles Passwort und zweimal das neue (6 bis 72 Zeichen), der Server prüft das aktuelle. Andere angemeldete Geräte müssen sich danach neu anmelden, diese App bleibt angemeldet
- Administratoren (Konto in `web/api/admins.local.php`) sehen im Menü „Stammdaten aktualisieren“: Die Liste zeigt, welche Charaktere, Lichtkegel und Relikt-Sets in der Datenbank fehlen; die ausgewählten werden samt Bildern eingetragen (wie in der Web-Version)
- Neue Einträge des Servers erscheinen in der App ohne neuen Build: Die App gleicht ihre Stammdaten nach dem Laden mit dem Server ab und speichert fehlende Einträge samt Bildern im app-privaten Speicher

## Android 1.0.0 (14.11.2026)
- Erste Version der Android-App (Kotlin, Jetpack Compose), Online-Client der Web-Version: Anmelden, Registrieren, Konto löschen, Teamliste, Build-Ansicht sowie Bearbeiten und Speichern von Builds und Teams, Offline-Ansicht aus dem Zwischenspeicher
- Builds mit Lichtkegel (Level, S1–S5) und sechs Relikten, aktive Set-Boni aus den Slots berechnet; dasselbe Austauschformat wie Web und Desktop

## Desktop 1.1.0 (14.11.2026)
- Tooltips mit den Beschreibungen: am Lichtkegel die Fähigkeit passend zur gewählten Überlagerung (S1–S5) und in der Lichtkegel-Suche die Fähigkeit bei S1. Die Set-Boni der Relikte sind jetzt auf Deutsch
- Bestehende Datenbanken bekommen die neue Spalte `light_cones.passive_json` und die deutschen Texte beim Start automatisch

## Desktop 1.0.0 (14.11.2026)
- Erste Version der Desktop-App (Python, PySide6, SQLite): Teams mit bis zu vier Charakteren, Builds mit Level, Eidolon, Talenten, Lichtkegel (Level, S1–S5) und sechs Relikten (Set, Level, Hauptwert, vier Substats). Die aktiven 2er-/4er-Set-Boni stehen auf der Karte
- Suche und Filter für Charaktere (Pfad, Element, Seltenheit) und Lichtkegel (Pfad, Seltenheit)
- Export und Import im selben Format wie die Web-Version (`star-rail-team-manager`, Version 1)
- Stammdaten aus `shared/stammdaten.json`, werden beim ersten Start eingespielt

## Web 1.1.0 (14.11.2026)
- Tooltips mit den Beschreibungen: am Lichtkegel die Fähigkeit passend zur gewählten Überlagerung (S1–S5), in der Lichtkegel-Suche die Fähigkeit bei S1. Die Set-Boni der Relikte sind jetzt auf Deutsch
- Die Fähigkeiten liegen in der Datenbank (neue Spalte `light_cones.passive_json`, Migration `007_lichtkegel_faehigkeiten.sql` mit `python3 tools/remote_sql.py --ddl …`, danach `web/seed_stammdaten.sql`); ohne die Migration fehlen nur die Tooltips. Neue Lichtkegel und Sets bekommen ihre deutschen Texte beim Eintragen über das Skript und das Menü automatisch
- `python3 tools/update_stammdaten.py --texts` liest die Fähigkeiten und Set-Boni aller vorhandenen Einträge neu aus der deutschen Quelle ein

## Web 1.0.0 (14.11.2026)
- Erste Version der Web-Version (PHP, MySQL): Konten, Teams, Builds, Export/Import, Stammdaten-Aktualisierung für Administratoren, Passwort ändern
