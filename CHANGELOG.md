# Änderungsverlauf

Dieser Verlauf fasst die wichtigsten Änderungen zusammen. Web, Desktop und Android haben ab 1.2.1 wieder dieselbe Versionsnummer (davor liefen sie getrennt); die Web-Version läuft per `deploy/deploy.sh` auf dem Server, der Desktop direkt aus dem Repo.

## 1.4.2 (15.11.2026)
- Android: Auch in der Set-Auswahl erscheint ein gesuchtes Relikt-Teil als eigene Zeile (wie im Web seit 1.4.1), z. B. „Vielgesichtige Maske des Diebs“ mit „Kopf · Dieb des fallenden Meteors“; Antippen übernimmt das Set

## 1.4.1 (15.11.2026)
- Web: In der Set-Auswahl erscheint ein gesuchtes Relikt-Teil als eigene Zeile (z. B. „Vielgesichtige Maske des Diebs“, darunter „Kopf · Dieb des fallenden Meteors“); ein Klick trägt das zugehörige Set ein. Teile erscheinen nur im passenden Slot-Typ (Relikt-Teile bei Kopf bis Füße, Ornament-Teile bei Planarsphäre und Verbindungsseil)

## 1.4.0 (14.11.2026)
- Die Namen der einzelnen Relikt-Teile stehen jetzt in den Stammdaten (auf Deutsch, z. B. „Vielgesichtige Maske des Diebs“ für das Kopfteil von „Dieb des fallenden Meteors“). Die Suche bei der Set-Auswahl findet ein Set auch über den Namen eines seiner Teile und zeigt das gefundene Teil unter dem Set an (Web und Android). Die Tooltips bzw. die Info-Anzeige nennen die Teile (Web, Desktop, Android)
- Neue Spalte `relic_sets.pieces_json`: Web per `web/migrations/010_relikt_teile.sql` (danach `web/seed_stammdaten.sql`), Desktop automatisch beim Start. Das Update-Werkzeug und das Admin-Menü tragen die Teile neuer Sets selbst ein; `python3 tools/update_stammdaten.py --texts` liest sie für vorhandene Sets neu ein

## 1.3.0 (14.11.2026)
- Lichtkegel und Relikt-Sets haben jetzt deutsche Namen (offizielle Namen aus StarRailRes, z. B. „Pfeile“, „Füllhorn“, „Im Wissen verlorene Gelehrte“). Charaktere bleiben englisch. Die Bilddateien behalten ihren englischen Namen und dienen dem Update-Werkzeug und dem Admin-Menü als stabile Kennung
- Bereits gespeicherte Builds werden umgestellt: Desktop beim ersten Start automatisch, Web per `web/migrations/009_deutsche_namen.sql` (danach `web/seed_stammdaten.sql`). Alte Export-Dateien mit englischen Namen lassen sich weiter importieren; auch ältere App-Versionen, die noch englische Namen senden, werden vom Server umgeschrieben (`shared/name_aliases.json`, `web/api/name_aliases.php`)

## 1.2.1 (14.11.2026)
Ab dieser Version tragen Web, Desktop und Android wieder dieselbe Versionsnummer.
- Die Auswahl der Relikt-Sets und Planar-Ornamente zeigt zu jedem Set sein Icon. Web: zusätzlich mit Suchfeld, das Icon steht auch im geschlossenen Feld, die Set-Boni bleiben als Tooltip an den Einträgen, Pfeiltasten, Enter und Escape funktionieren wie bei der Teil-Auswahl im Genshin-Manager. Desktop: die Dropdowns zeigen die Icons. Android zeigt die Set-Icons in der Auswahl bereits
- Android: Die Bilder liegen in den neuen Unterordnern (siehe Web 1.1.1); vom Server nachgeladene Bilder behalten ihre Unterordner

## Web 1.1.1 (14.11.2026)
- Die Bilder liegen nach Typ getrennt in Unterordnern von `shared/assets/images/` (`characters`, `light_cones`, `relic_sets`, `elements`, `paths`, `ui`), ohne das frühere Namenspräfix (aus `char_kafka.png` wird `characters/kafka.png`). Die Pfade in `stammdaten.json`, im Seed, im Update-Werkzeug und im Admin-Menü sind angepasst. Bereits eingetragene Pfade in der Server-Datenbank funktionieren weiter, solange die alten Dateien auf dem Server liegen.

## Desktop 1.1.1 (14.11.2026)
- Bilder werden aus den neuen Unterordnern geladen (siehe Web 1.1.1)

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
