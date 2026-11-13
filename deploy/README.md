# Deployment der Web-Version (star-rail.media-matrix.de)

`deploy.sh` lädt `web/` plus `shared/assets` per FTPS auf den Server. Es funktioniert wie beim Genshin-Manager: nur Geändertes wird hochgeladen, überschriebene Serverdateien landen vorher in `deploy/backup/<Zeitstempel>/`, auf dem Server wird nie etwas gelöscht.

```sh
cp deploy/deploy.example.env deploy/deploy.local.env   # einmalig ausfüllen (nicht im Git)
deploy/deploy.sh --dry-run
deploy/deploy.sh
```

## Einmalige Einrichtung der Subdomain

1. **Subdomain anlegen** (star-rail.media-matrix.de) mit eigenem Web-Root, HTTPS aktivieren, PHP mit PDO-MySQL.
2. **Datenbank anlegen** (Name, Benutzer, Passwort). In phpMyAdmin nacheinander importieren: `web/schema.sql`, dann `web/seed_stammdaten.sql`.
3. **Zugangsdaten auf dem Server** als `api/config.local.php` anlegen (Vorlage: `web/api/config.example.php`; `host` ist bei vielen Hostern `localhost`). Diese Datei lädt das Skript nie hoch und überschreibt sie auch nicht.
4. **`deploy/deploy.local.env` ausfüllen.** `FTP_HOST` ist der Servername mit gültigem Zertifikat (beim Hoster dogado `webNNN.dogado.net`), `FTP_DIR` das Web-Root der Subdomain.
5. `deploy/deploy.sh --dry-run`, dann `deploy/deploy.sh`.

Neue Stammdaten (neue Charaktere, Lichtkegel, Sets): `python3 web/tools/make_seed.py`, deploy ausführen, `web/seed_stammdaten.sql` erneut in phpMyAdmin importieren (wiederholbar, vorhandene Einträge werden aktualisiert).
