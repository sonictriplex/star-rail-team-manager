# Änderungsverlauf

Dieser Verlauf fasst die wichtigsten Änderungen zusammen. Web, Desktop und Android sind getrennte Clients mit eigenen Versionsnummern; die Web-Version läuft per `deploy/deploy.sh` auf dem Server, der Desktop direkt aus dem Repo.

## Android 1.0.0 (14.11.2026)
- Erste Version der Android-App (Kotlin, Jetpack Compose), Online-Client der Web-Version: Anmelden, Registrieren, Konto löschen, Teamliste, Build-Ansicht sowie Bearbeiten und Speichern von Builds und Teams, Offline-Ansicht aus dem Zwischenspeicher
- Builds mit Lichtkegel (Level, S1–S5) und sechs Relikten, aktive Set-Boni aus den Slots berechnet; dasselbe Austauschformat wie Web und Desktop

## Desktop 1.0.0 (14.11.2026)
- Erste Version der Desktop-App (Python, PySide6, SQLite): Teams mit bis zu vier Charakteren, Builds mit Level, Eidolon, Talenten, Lichtkegel (Level, S1–S5) und sechs Relikten (Set, Level, Hauptwert, vier Substats). Die aktiven 2er-/4er-Set-Boni stehen auf der Karte
- Suche und Filter für Charaktere (Pfad, Element, Seltenheit) und Lichtkegel (Pfad, Seltenheit)
- Export und Import im selben Format wie die Web-Version (`star-rail-team-manager`, Version 1)
- Stammdaten aus `shared/stammdaten.json`, werden beim ersten Start eingespielt

## Web 1.0.0 (14.11.2026)
- Erste Version der Web-Version (PHP, MySQL): Konten, Teams, Builds, Export/Import, Stammdaten-Aktualisierung für Administratoren, Passwort ändern
