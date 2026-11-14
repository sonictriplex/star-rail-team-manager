# Web-App (Honkai: Star Rail – Team & Build Manager)

Web-App zum Zusammenstellen von Star-Rail-Teams und Verwalten der Charakter-Builds. Jeder Nutzer hat ein eigenes Konto und eigene Teams. Die Live-Version läuft unter <https://star-rail.media-matrix.de>; dieselbe API bedient die [Android-App](../android/README.md).

## Funktionen

- Registrierung, Login, **Passwort ändern**, Logout und **Konto löschen** (Session-basiert im Browser, Token für Apps)
- Teams mit bis zu 4 Charakteren anlegen, speichern, umbenennen und löschen; Teams und Charakterkarten per Drag & Drop oder mit Pfeilen umsortieren
- Pro Charakter: Level, Eidolon, Talente, Lichtkegel (Level, Überlagerung S1–S5) und sechs Relikte mit Set, Seltenheit, Level, Hauptwert und vier Substats
- Aktive Set-Boni werden aus den Slots berechnet; **Tooltips** zeigen die Fähigkeit des Lichtkegels (zur gewählten Überlagerung) und die Set-Boni
- Suchbare Auswahllisten für Charaktere (Pfad, Element, Seltenheit) und Lichtkegel (Pfad, Seltenheit)
- Benutzermenü: Alle Teams, Neues Team, Exportieren, Importieren, Passwort ändern, Logout, Konto löschen, Über; für **Administratoren** zusätzlich **Stammdaten aktualisieren**

Builds werden **pro Nutzer und Charakter** gespeichert, nicht pro Team. Ändert man den Build eines Charakters, gilt die Änderung in allen Teams, in denen er vorkommt.

## Technik

- **Frontend:** eine einzelne `index.html` mit Vanilla JavaScript und Tailwind CSS (CDN). Alle Texte sind deutsch; Werte aus der Datenbank oder von Nutzern gehen vor dem Einfügen per `innerHTML` durch `escapeHtml()`
- **Backend:** PHP-Endpunkte in `api/` (PDO mit nativen Prepared Statements, JSON-API, kein Framework)
- **Datenbank:** MySQL / MariaDB
- Es gibt keinen Build-Schritt und keine Abhängigkeiten, die installiert werden müssen.

```
index.html          Komplettes Frontend
api/
  db.php              Datenbankverbindung (Zugangsdaten aus config.local.php oder Umgebung)
  auth_check.php      Session-Setup, Bearer-Token, require_login(), is_admin(), require_admin()
  auth.php            ?action=status|register|login|token|logout|change_password|delete_account
  rate_limit.php      Begrenzung von Anmelde- und Registrierungsversuchen
  characters.php      Charakterliste (GET, öffentlich)
  light_cones.php     Lichtkegel mit Fähigkeit (GET, öffentlich)
  relic_sets.php      Relikt-Sets und Planar-Ornamente mit Boni (GET, öffentlich)
  teams.php           Teams des Nutzers (GET / POST / DELETE)
  transfer.php        Export und Import der Builds und Teams (GET / POST)
  stammdaten.php      Stammdaten abgleichen und ergänzen (nur Administratoren)
  stammdaten_lib.php  Helfer dafür (Abruf der Quelle, Bilder speichern)
  helpers.php         Eingaben prüfen und begrenzen (gemeinsam für teams.php und transfer.php)
schema.sql          Datenbankschema
seed_stammdaten.sql Stammdaten als SQL (erzeugt von tools/make_seed.py, wiederholbar einspielbar)
migrations/         SQL-Änderungen für bestehende Datenbanken
tools/make_seed.py  erzeugt den Seed aus ../shared/stammdaten.json
assets/             Symlink auf ../shared/assets (Bilder)
```

## Einrichtung

1. **Webserver mit PHP 8** (z. B. Apache oder nginx + PHP-FPM) mit den Erweiterungen PDO-MySQL, mbstring und curl. Der Inhalt von `web/` muss im **Web-Root** liegen (beim Hochladen den Symlink `assets` auflösen, z. B. `rsync -rL web/ …`), weil das Frontend absolute Pfade (`/api`, `/assets/images`) verwendet. Für den Betrieb dieses Projekts übernimmt das `../deploy/deploy.sh`.
2. **Datenbank** anlegen und nacheinander `schema.sql` und `seed_stammdaten.sql` einspielen. `schema.sql` enthält schon alle Tabellen (auch `api_tokens` und `auth_attempts`); die Dateien in `migrations/` brauchen nur bestehende Datenbanken.
3. **Zugangsdaten** konfigurieren, entweder per Datei:

   ```sh
   cp api/config.example.php api/config.local.php
   # Werte in api/config.local.php eintragen
   ```

   oder per Umgebungsvariablen (haben Vorrang): `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASS`, `DB_CHARSET`. `api/config.local.php` steht in der `.gitignore`.
4. **Administratoren** (optional) eintragen in `api/admins.local.php`:

   ```php
   <?php return ['benutzername'];
   ```

   oder per Umgebungsvariable `ADMIN_USERS` (kommagetrennt). Die Datei liegt nicht im Git und wird vom Deploy nie hochgeladen.
5. Für einen schnellen lokalen Test reicht der eingebaute PHP-Server:

   ```sh
   php -S localhost:8000
   ```

   Danach <http://localhost:8000> öffnen.

## Datenmodell

Die Tabellen stehen in `schema.sql`:

| Tabelle | Inhalt |
|---|---|
| `users` | Konten (`username` eindeutig, `password_hash` als bcrypt) |
| `characters` | Stammdaten: Name, Element, Pfad (ENUM), Seltenheit, Icon |
| `light_cones` | Stammdaten: Name, Pfad (ENUM), Seltenheit, Icon, Fähigkeit als JSON in `passive_json` |
| `relic_sets` | Stammdaten: Name, Art (`Relikt` oder `Ornament`), 2er- und 4er-Bonus, Icon |
| `user_characters` | Builds, eindeutig je `(user_id, char_name)`; die Relikte stehen als JSON in `relics_json` |
| `teams`, `team_members` | Teams (eindeutig je `(user_id, name)`, Reihenfolge in `position`) und ihre Mitglieder (`slot_order` 0–3) |
| `api_tokens` | Token-Login der Apps (nur SHA-256-Hash) |
| `auth_attempts` | Zähler für die Begrenzung der Anmeldeversuche (nur Hashes) |

Team-Mitglieder werden über den **Charakternamen** mit den Stammdaten verbunden, nicht über eine ID. `relics_json` enthält je Slot (`Kopf`, `Hände`, `Körper`, `Füße`, `Planarsphäre`, `Verbindungsseil`) `{set, rarity, level, main, main_value, substats: [{stat, val} ×4]}`. Slots 1–4 nehmen Sets der Art `Relikt`, Slots 5–6 der Art `Ornament`.

## API

Alle Antworten sind JSON; Fehler haben die Form `{"error": "…"}` mit einem passenden HTTP-Status. Fehlermeldungen enthalten nie interne Details, die gehen nur ins Server-Log.

| Endpunkt | Methode | Zweck |
|---|---|---|
| `auth.php?action=status` | GET | `{logged_in, username, user_id, is_admin}` |
| `auth.php?action=register` | POST `{username, password}` | Konto anlegen (mind. 3 bzw. 6 Zeichen) und Session starten |
| `auth.php?action=login` | POST `{username, password}` | Session starten |
| `auth.php?action=token` | POST `{username, password, device?}` | Token für Apps (siehe unten) |
| `auth.php?action=logout` | POST | Session beenden bzw. Token widerrufen |
| `auth.php?action=change_password` | POST `{old_password, new_password}` | Passwort ändern |
| `auth.php?action=delete_account` | POST `{password}` | Konto samt Daten löschen |
| `characters.php`, `light_cones.php`, `relic_sets.php` | GET | Stammdaten (öffentlich, ohne Anmeldung) |
| `teams.php` | GET | `{Teamname: [{character, saved}, …]}` in der gespeicherten Reihenfolge |
| `teams.php` | POST `{name, members}` | Team anlegen oder überschreiben; die Builds der Mitglieder werden in einer Transaktion mitgespeichert |
| `teams.php` | POST `{order: [Teamname, …]}` | Reihenfolge der Teams speichern |
| `teams.php?name=` | DELETE | Team löschen |
| `transfer.php` | GET | Export aller Builds und Teams (JSON-Datei) |
| `transfer.php` | POST `{mode: merge\|replace, data}` | Import in einer Transaktion, Ergebnis `{builds, teams, skipped}` |
| `stammdaten.php?action=check` | GET | **Administratoren:** was in der Datenbank fehlt |
| `stammdaten.php` | POST `{characters, light_cones, relic_sets}` | **Administratoren:** ausgewählte Einträge eintragen |

Was jeder Endpunkt mit Nutzerdaten verlangt: eine gültige Session oder einen gültigen Token (sonst **401**). Das Austauschformat von `transfer.php` steht in [../shared/exchange-format.md](../shared/exchange-format.md).

### Eingaben und Grenzen

Der Server begrenzt alles selbst (`helpers.php`), unabhängig vom Frontend: Level 1–80, Eidolon 0–6, Talente 1–7 (Basis) bzw. 1–12, Lichtkegel-Level 1–80, Überlagerung 1–5, Relikt-Level 0–15, Relikt-Seltenheit 2–5, höchstens 4 Mitglieder je Team, keine Duplikate im Team, Teamname höchstens 255 Zeichen. Platzhaltertexte wie „— Kein Relikt-Set —“ werden zu einem leeren Wert, unbekannte Slots verworfen und immer genau vier Substats gespeichert. Leere PHP-Arrays, die das Frontend als Objekt erwartet, gibt der Server als `{}` aus.

## Token-Login für Apps

Clients ohne Cookies (z. B. die Android-App) melden sich über `POST api/auth.php?action=token` mit `{username, password, device?}` an und erhalten `{token, expires_at, username}`. Danach senden sie bei jeder Anfrage den Token mit, entweder als `Authorization: Bearer <token>` oder als `X-Auth-Token: <token>`; alle Endpunkte mit `require_login()` akzeptieren das wie eine Session. Ein Token gilt 90 Tage, pro Nutzer bleiben höchstens 10 gültig, in der Datenbank steht nur der SHA-256-Hash. `POST api/auth.php?action=logout` mit Token widerruft ihn. **Nur über HTTPS betreiben.**

**Warum zwei Header?** Viele Hoster reichen den `Authorization`-Header nicht an PHP durch (nginx mit PHP-FPM, Apache mit CGI), eigene Header kommen dagegen immer an. Die Android-App sendet deshalb beide, und `auth_check.php` nimmt den Bearer-Token, wenn er ankommt, sonst `X-Auth-Token`. Ein ungültiger Token bedeutet „nicht eingeloggt“, ohne Rückfall auf eine Session.

## Passwort ändern und Konto löschen

**Passwort ändern:** `POST api/auth.php?action=change_password` mit `{old_password, new_password}` (Session oder Token). Das neue Passwort muss 6 bis 72 Zeichen lang sein (bcrypt ignoriert alles ab dem 73. Byte, deshalb wird mehr abgelehnt) und sich vom alten unterscheiden. Das aktuelle Passwort wird geprüft; ein falsches ergibt **403** und zählt für die Begrenzung wie ein fehlgeschlagener Login. Bei Erfolg werden **alle anderen Tokens** des Nutzers ungültig (ein Token der laufenden Anfrage bleibt), die Session-ID wird erneuert.

**Konto löschen:** `POST api/auth.php?action=delete_account` mit `{password}` löscht den Nutzer samt Teams, Teammitgliedern, Builds und allen Tokens in einer Transaktion und beendet die Session. Das Passwort muss erneut stimmen; ein falsches ergibt 403 (bewusst nicht 401, das die Apps als „Sitzung abgelaufen“ deuten) und zählt wie ein fehlgeschlagener Login.

## Administratoren und Stammdaten

`stammdaten.php` gleicht die Datenbank mit der öffentlichen Datenquelle [Mar-7th/StarRailRes](https://github.com/Mar-7th/StarRailRes) ab und trägt auf Wunsch Fehlendes samt Bildern ein. Das geht nur für Nutzer aus `api/admins.local.php` (`require_admin()` antwortet sonst mit **403**).

- **`GET ?action=check`** liefert `{new: {characters, light_cones, relic_sets}, skipped: […]}`. Eintragbar sind nur Einträge mit bekanntem Pfad und Element; alles andere steht in `skipped`.
- **`POST`** nimmt nur die **Namen** der gewählten Einträge an. Der Server rechnet die Einträge neu aus der Quelle, dem Browser wird nichts geglaubt. Die Anfrage muss den Header `X-Requested-With: fetch` tragen (Schutz vor fremden Formularen), höchstens 50 Einträge je Anfrage, Antwort `{added, images: {geladen, vorhanden, fehler}}`.
- Neue Lichtkegel und Sets bekommen ihre **deutschen** Fähigkeiten bzw. Boni aus der Quelle; die Texte werden serverseitig mit denselben Regeln wie in `tools/update_stammdaten.py` aufbereitet.
- Bilder lädt der Server nach `assets/images/` (nur gültige PNG-Dateien, vorhandene werden nie überschrieben). Der Ordner muss für PHP beschreibbar sein.
- `shared/stammdaten.json` im Repository ändert der Endpunkt **nicht**; ein späterer Lauf von `tools/update_stammdaten.py` holt das lokal nach.

## Begrenzung von Anmeldungen und Registrierungen

Schutz vor Durchprobieren von Passwörtern und vor massenhaftem Anlegen von Konten (`api/rate_limit.php`, Tabelle `auth_attempts`). Bei Überschreitung antwortet die API mit **HTTP 429**, dem Header `Retry-After` und einer deutschen Meldung („Zu viele Anmeldeversuche. Bitte versuche es in 12 Minuten erneut.“), die Web-Oberfläche und die Android-App anzeigen.

| Aktion | Gezählt | Grenze | Fenster |
|---|---|---|---|
| Anmeldung (Session und Token), Passwort ändern, Konto löschen | Fehlversuche je Benutzername | 8 | 15 Minuten |
| dasselbe | Fehlversuche je IP-Adresse | 15 | 15 Minuten |
| Registrierung | Versuche je IP-Adresse | 5 | 1 Stunde |
| Registrierung | Versuche insgesamt | 60 | 1 Stunde |

- Gesperrt wird auch bei **richtigem** Passwort, bis die Sperre abläuft; eine erfolgreiche Anmeldung setzt den Zähler des Benutzernamens zurück. Ein unbekannter Benutzername wird genauso gezählt.
- Die Werte stehen als Konstanten am Anfang von `rate_limit.php`. Gespeichert werden nur SHA-256-Werte von IP und Benutzername, nie Klartext; Zeilen werden nach spätestens 2 Stunden gelöscht.
- **Client-IP:** Hinter einem Reverse-Proxy (nginx) steht in `REMOTE_ADDR` die Adresse des Proxys. Deshalb gilt `X-Real-IP` bzw. das erste `X-Forwarded-For`, aber nur, wenn die Verbindung von einer lokalen oder privaten Adresse kommt (sonst ließe sich der Header fälschen).
- **Ausfallsicher:** Fehlt die Tabelle oder schlägt die Zählung fehl, wird nicht gesperrt und die Anmeldung funktioniert wie zuvor.
- **Grenze des Verfahrens:** Wer den Namen eines Kontos kennt, kann es durch absichtliche Fehlversuche für 15 Minuten sperren. Das ist der Preis der Sperre je Benutzername.

## Migrationen

Für **neue** Datenbanken reicht `schema.sql` plus `seed_stammdaten.sql`. Die Dateien in `migrations/` sind der Verlauf für **bestehende** Datenbanken; jede einmal ausführen, am bequemsten mit `python3 ../tools/remote_sql.py [--ddl] migrations/<datei>.sql`:

| Datei | Inhalt | `--ddl` |
|---|---|---|
| `001_pfadnamen.sql` | Pfade „Nichtigkeit“ und „Überfluss“ statt Nichtsein und Fülle | ja |
| `002_pfadnamen_gelehrsamkeit_gedenken.sql` | Pfade „Gelehrsamkeit“ und „Gedenken“ statt Erudition und Erinnerung | ja |
| `003_scholar_set_name.sql` | Korrektur eines versehentlich umbenannten Relikt-Sets | nein |
| `004_pfadnamen_mit_artikel.sql` | Pfade mit Artikel wie im Spiel (Die Zerstörung, Der Überfluss, Das Gedenken …) | ja |
| `006_march7th_trailblazer_bereinigen.sql` | zwei Charaktere gleichen Namens trennen, verwaisten Trailblazer entfernen | nein |
| `007_lichtkegel_faehigkeiten.sql` | Spalte `light_cones.passive_json` (Tooltips der Lichtkegel); danach `seed_stammdaten.sql` | ja |

`--ddl` erlaubt zusätzlich `ALTER TABLE … MODIFY` und `ADD COLUMN` (ohne Transaktion); ohne diesen Schalter sind nur INSERT, UPDATE, DELETE und REPLACE möglich. Der Code ist so geschrieben, dass er ohne `007` weiterläuft: `light_cones.php` liefert dann die Lichtkegel ohne Fähigkeit.

## Lokal gegen echtes MariaDB testen

Ohne eigenen MySQL-Server lässt sich die API mit Podman prüfen (Port 3306 muss frei sein):

```sh
podman run -d --name sr-mariadb -e MARIADB_ROOT_PASSWORD=testroot -e MARIADB_DATABASE=star-rail \
  -p 127.0.0.1:3306:3306 docker.io/library/mariadb:lts
podman exec -i sr-mariadb mariadb -uroot -ptestroot star-rail < web/schema.sql
podman exec -i sr-mariadb mariadb -uroot -ptestroot star-rail < web/seed_stammdaten.sql
cd web && DB_HOST=127.0.0.1 DB_NAME=star-rail DB_USER=root DB_PASS=testroot \
  php -d extension=pdo_mysql -S 127.0.0.1:8000
```

Der Android-Emulator erreicht den Rechner unter `http://10.0.2.2:8000`. Aufräumen: `podman rm -f sr-mariadb`. Auf Arch Linux ist `pdo_mysql` installiert, aber in der `php.ini` deaktiviert, daher der Parameter `-d extension=pdo_mysql`.

## Bilder

Bilder liegen in `../shared/assets/images/`: `char_<name>.png`, `lc_<name>.png`, `relic_<name>.png`, dazu `path_<pfad>.png` und `element_<element>.png`. Der Name wird kleingeschrieben, alles außer a–z und 0–9 wird zu `_` (aus „Kafka“ wird `char_kafka.png`, aus „Der Überfluss“ wird `path_ueberfluss.png` ohne Artikel und mit ue). Der Hintergrund ist `star-rail-bg.jpg`. Die Bilder von neuen Einträgen legt `tools/update_stammdaten.py` bzw. der Menüpunkt „Stammdaten aktualisieren“ selbst an.

## Hinweis

Inoffizielles Fanprojekt, nicht mit HoYoverse verbunden. Honkai: Star Rail sowie zugehörige Namen und Bilder sind Eigentum von HoYoverse.
