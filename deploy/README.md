# Deployment der Web-Version (star-rail.media-matrix.de)

`deploy.sh` lädt die Web-Version (`web/` plus `shared/assets`) per FTPS auf den Server.

```sh
cp deploy/deploy.example.env deploy/deploy.local.env   # einmalig ausfüllen (nicht im Git)
deploy/deploy.sh --dry-run    # zeigt nur, was sich ändern würde
deploy/deploy.sh              # lädt Geändertes hoch
```

## Verhalten
- **Nur Geändertes:** Verglichen wird bei Bildern nach Größe, bei `*.php` und `*.html` nach Inhalt.
- **Sicherung:** Dateien, die überschrieben werden, landen vorher in `deploy/backup/<Zeitstempel>/`.
- **Nie gelöscht:** Dateien auf dem Server, die es lokal nicht gibt, bleiben unberührt. Muss auf dem Server etwas weg (z. B. ein nicht mehr gebrauchtes Bild), löscht man es von Hand per FTP.
- **Nie hochgeladen:** `api/config.local.php` (Datenbank-Zugangsdaten) und `api/admins.local.php` (Administratoren), dazu `api/config.example.php`. Beide Dateien legt man einmalig auf dem Server an. `api/db.php` enthält keine Zugangsdaten und wird mit hochgeladen.
- **Danach:** Das Skript prüft, ob die Startseite mit HTTP 200 antwortet.
- Das Passwort wird nicht in der Prozessliste sichtbar: Es geht über eine temporäre Datei mit Rechten nur für den Benutzer.

## Zugangsdaten (`deploy.local.env`, nicht im Git)
| Variable | Bedeutung |
|---|---|
| `FTP_HOST` | Servername mit gültigem TLS-Zertifikat. Beim Hoster dogado nicht die eigene Domain, sondern `webNNN.dogado.net` (die Domain hat kein passendes Zertifikat) |
| `FTP_USER`, `FTP_PASS` | FTP-Zugang |
| `FTP_DIR` | Web-Root der Subdomain relativ zur FTP-Wurzel, ohne Schrägstriche am Rand |
| `SITE_URL` | wird nach dem Upload geprüft und von `tools/remote_sql.py` für den Aufruf benutzt |

Die Dateien `deploy.local.env` und `backup/` stehen in `.gitignore`. In `backup/` liegen u. a. frühere Serverstände, die nie ins Git gehören.

## Einmalige Einrichtung der Subdomain

1. **Subdomain anlegen** (z. B. star-rail.media-matrix.de) mit eigenem Web-Root, HTTPS aktivieren, PHP 8 mit PDO-MySQL, mbstring und curl. Der Web-Root-Ordner muss für PHP beschreibbar sein, soweit `assets/images` betroffen ist (dorthin lädt „Stammdaten aktualisieren“ neue Bilder).
2. **Datenbank anlegen** (Name, Benutzer, Passwort) und `web/schema.sql` einspielen, am einfachsten in phpMyAdmin. Danach sind die Stammdaten dran (Schritt 6).
3. **Zugangsdaten auf dem Server** als `api/config.local.php` anlegen (Vorlage: `web/api/config.example.php`; `host` ist bei vielen Hostern `localhost`). Diese Datei lädt das Skript nie hoch und überschreibt sie auch nicht.
4. **`deploy/deploy.local.env` ausfüllen** (siehe oben).
5. `deploy/deploy.sh --dry-run`, dann `deploy/deploy.sh`.
6. **Stammdaten einspielen:** `python3 tools/remote_sql.py web/seed_stammdaten.sql` (braucht den deployten Code und die `config.local.php`).
7. **Administratoren** (optional): `api/admins.local.php` mit `<?php return ['benutzername'];` auf dem Server anlegen. Ohne diese Datei gibt es keinen Administrator.

## Datenbank ohne phpMyAdmin ändern

Die Datenbank ist nur vom Server aus erreichbar. `python3 tools/remote_sql.py <datei>.sql` führt INSERT, UPDATE, DELETE und REPLACE auf dem Server aus, mit `--ddl` zusätzlich `ALTER TABLE … MODIFY` und `ADD COLUMN`:

1. Das Skript lädt per FTPS eine einmalige PHP-Datei (`api/_sql_<zufall>.php`) hoch. Sie enthält nur den SHA-256-Hash eines Zufalls-Tokens, die Anweisungen und nutzt die `api/db.php` des Servers.
2. Es ruft sie per HTTPS mit dem Token auf. Alle Anweisungen laufen in **einer Transaktion** (bei `--ddl` der Reihe nach, weil `ALTER TABLE` Transaktionen beendet); bei einem Fehler bleibt alles unverändert.
3. Die Datei löscht sich selbst; das Skript prüft per FTP-Liste nach und entfernt sie sonst.

Weitere Anweisungen (z. B. `DROP`) lehnt das Skript ab.

## Neue Stammdaten

```sh
python3 tools/update_stammdaten.py --auto
```

Das ergänzt Fehlendes, deployt, schreibt in die Datenbank und prüft über die API (Details in der [README](../README.md#stammdaten-aktualisieren)). Ohne `--auto`: `python3 web/tools/make_seed.py`, `deploy/deploy.sh`, dann `python3 tools/remote_sql.py web/seed_stammdaten.sql` (wiederholbar, vorhandene Einträge werden aktualisiert).

## Datenbank-Änderungen (Migrationen)

Änderungen für bestehende Datenbanken stehen in `web/migrations/` (Liste und Reihenfolge in [web/README.md](../web/README.md#migrationen)). Jede Migration einmal ausführen, am bequemsten mit `tools/remote_sql.py`.

## Wiederherstellen (alten Stand zurückspielen)
Die gesicherte Datei aus `backup/<Zeitstempel>/` wieder hochladen, oder den alten Stand aus dem Git holen (`git checkout <Commit> -- web/index.html`) und `deploy.sh` ausführen.
