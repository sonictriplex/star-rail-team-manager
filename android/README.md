# Android-App (Honkai: Star Rail)

Online-Client für die Web-Version. Die App greift **nicht** direkt auf MySQL zu, sondern über die PHP-API in `../web/api/` (Anmeldung per Token, siehe `../web/README.md`).

**Stand (Version 1.2.1):** Anmelden, Registrieren, **Passwort ändern** und Konto löschen, Teamliste, Build-Ansicht sowie Bearbeiten und Speichern von Builds und Teams (anlegen, ändern, umbenennen, löschen) funktionieren. Teams und Builds lassen sich auch offline ansehen. **Administratoren** können neue Charaktere, Lichtkegel und Relikt-Sets eintragen, und die App gleicht ihre Stammdaten mit dem Server ab.

## Technik

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- OkHttp und kotlinx.serialization für die API; Benutzername in app-privaten SharedPreferences, der Token verschlüsselt mit einem AES-Schlüssel aus dem Android Keystore (`TokenCipher`). Ist der Schlüssel ungültig, gilt man als abgemeldet; ein Klartext-Token aus Version 0.1 wird beim ersten Start verschlüsselt
- `minSdk` 26, `compileSdk` 37, Gradle 9.6 (Wrapper), Android Gradle Plugin 9.5

## Registrierung

Auf der Anmeldeseite wechselt „Noch kein Konto? Registrieren“ in den Modus „Konto erstellen“ (zusätzlich „Passwort wiederholen“). Das Konto ist dasselbe wie in der Web-Version. Vorab prüft die App Benutzername (mindestens 3 Zeichen), Passwort (mindestens 6) und die Wiederholung; Serverfehler wie „Dieser Benutzername ist bereits vergeben.“ erscheinen unverändert. Die API liefert bei `auth.php?action=register` nur eine Session, keinen Token; die App meldet sich deshalb direkt danach per Token an (`ApiClient.register`). Scheitert nur dieser zweite Schritt, weist die Meldung darauf hin, dass das Konto angelegt ist und man sich normal anmelden kann. Die Registrierung ist, wie in der Web-Version, für jeden offen, aber auf dem Server begrenzt (siehe `web/README.md`); die App zeigt die Meldung bei zu vielen Versuchen unverändert an.

## Server-Adresse

Die Anmeldeseite hat in **allen** Varianten ein Feld „Server“, vorbelegt mit der zuletzt benutzten Adresse, sonst mit `BuildConfig.DEFAULT_SERVER_URL` aus `app/build.gradle.kts` (aktuell `https://star-rail.media-matrix.de`), angezeigt mit abschließendem „/“. Eingaben werden getrimmt und ohne „/“ am Ende gespeichert. Release-Builds erlauben nur HTTPS (Netzwerkkonfiguration), Debug und Staging zusätzlich HTTP zu `10.0.2.2` und `localhost` für lokale Testserver. Bei einem Umzug der Web-Version die Voreinstellung in `build.gradle.kts` ändern und neu bauen.

## Offline-Cache

Die zuletzt geladenen Teams und Builds liegen im app-privaten Speicher (`files/userdata-cache.json`, im Austauschformat, gebunden an Server und Benutzernamen; `data/UserDataCache.kt`).

- Beim Start erscheinen sie **sofort**, im Hintergrund wird aktualisiert (dünner Fortschrittsbalken).
- Ist der Server nicht erreichbar, bleibt der Stand sichtbar, mit dem Hinweis „Offline – Stand vom …“. **Bearbeiten ist dann gesperrt** (Neues Team, Bearbeiten, Löschen), bis wieder eine Verbindung besteht. Ein Abgleich eigener Änderungen aus dem Offline-Betrieb gibt es bewusst nicht, das bräuchte Konfliktlösung.
- Beim Zurückkehren in die App und über „Neu laden“ wird erneut versucht; gelingt es, verschwindet der Hinweis.
- Eigene Änderungen (Speichern, Löschen) aktualisieren den Cache.
- Beim **Abmelden** und bei abgelaufener Sitzung wird der Cache gelöscht. Er ist nicht verschlüsselt (anders als der Token), enthält aber nur Teams und Builds, keine Zugangsdaten.
- Nur ohne Cache und ohne Verbindung (erster Start) erscheint die Fehlermeldung statt der Liste.

## Passwort ändern

Im Menü (⋮) auf der Teamliste: „Passwort ändern“ (nur mit Verbindung). Der Dialog verlangt das aktuelle Passwort und zweimal das neue (6 bis 72 Zeichen, nicht gleich dem alten); die App prüft das vorab wie der Server und sendet `POST auth.php?action=change_password` mit `{old_password, new_password}`. Ein falsches aktuelles Passwort antwortet der Server mit 403, die Meldung erscheint im Dialog (kein „Sitzung abgelaufen“). Nach Erfolg bleibt **diese** App angemeldet (ihr Token bleibt gültig), alle anderen Geräte müssen sich neu anmelden.

## Stammdaten aktualisieren (Administratoren)

Ist das Konto auf dem Server als Administrator eingetragen (`web/api/admins.local.php`), erscheint im Menü „Stammdaten aktualisieren“. Die App fragt den Admin-Status nach jedem erfolgreichen Laden über `auth.php?action=status` ab (`is_admin`); deshalb steht der Punkt erst nach dem ersten Laden der Teams da. Der Dialog ruft `stammdaten.php?action=check` auf und listet, was in der Datenbank fehlt (alles vorausgewählt); „Ausgewählte eintragen“ sendet die Namen per `POST stammdaten.php` mit dem Header `X-Requested-With: fetch`. Der Server rechnet die Einträge selbst aus der Quelle aus und lädt die Bilder auf den Server. Das Menü ist nur mit Verbindung bedienbar.

## Stammdaten der App und Abgleich mit dem Server

Charaktere, Lichtkegel und Relikt-Sets stammen aus `shared/stammdaten.json` (in der APK). Damit neue Einträge, die ein Administrator eingetragen hat, ohne neuen Build erscheinen, gleicht die App nach dem Laden der Teams die öffentlichen Listen des Servers (`characters.php`, `light_cones.php`, `relic_sets.php`) ab: Was weder in den Assets noch schon gespeichert ist, landet in `files/stammdaten-extra.json`, die Bilder in `files/extra-images/` (`StammdatenRepository.syncFromServer`). Fehler (kein Netz) sind unkritisch, der alte Stand bleibt. Die Oberfläche folgt automatisch (die Stammdaten sind ein Compose-State).

## Konto löschen

Im Menü (⋮) auf der Teamliste: „Konto löschen“ (nur mit Verbindung). Der Dialog verlangt das Passwort; die App sendet `POST auth.php?action=delete_account`. Ein falsches Passwort erscheint im Dialog (der Server antwortet mit 403, nicht 401, sonst würde die App „Sitzung abgelaufen“ annehmen). Nach Erfolg wird lokal abgemeldet, der Cache gelöscht und der Benutzername im Anmeldefeld geleert. Das Konto ist auch in der Web-Version weg.

## Token-Header

Die App sendet den Token in zwei Headern: `Authorization: Bearer …` und `X-Auth-Token`. Hintergrund: Viele Hoster reichen den Authorization-Header nicht an PHP durch, der eigene Header kommt dagegen immer an. Der Server nimmt, was er bekommt (`web/api/auth_check.php`).

## Tooltips

Auf dem Handy gibt es keinen Hover: Die Beschreibung der **Lichtkegel-Fähigkeit** (passend zur gewählten Überlagerung S1–S5) und die **Set-Boni** der Relikte lassen sich über „ⓘ Beschreibung anzeigen“ aufklappen (Build-Ansicht und Bearbeiten); in den Auswahllisten stehen die ersten Zeilen unter dem Namen. Die Texte sind deutsch und stammen aus den Stammdaten.

## Aussehen

Immer dunkel wie Desktop und Web (Palette aus `desktop/styles.py`), mit dem Hintergrundbild `shared/assets/images/ui/star-rail-bg.jpg`. Charakterkarten haben einen Verlauf in der Elementfarbe und einen Rahmen nach Seltenheit (5★ golden, 4★ violett).

## Bearbeiten

Teams und Builds lassen sich bearbeiten; die Reihenfolge der Teammitglieder ändert man mit den Pfeilen unter den Icons. Beim Verlassen mit ungespeicherten Änderungen (Abbrechen oder Zurück) fragt die App nach.

## Gemeinsame Daten

`app/build.gradle.kts` bindet `../shared` als Asset-Ordner ein. Bilder (`assets/images/…`) und `stammdaten.json` liegen in der APK unter denselben Pfaden wie im Repo. Spezifikationen und Testdaten sind ausgeschlossen. Die Daten werden also nicht kopiert; neue Bilder oder Stammdaten wirken nach einem neuen Build.

Teams und Builds kommen über `api/transfer.php` im [Austauschformat](../shared/exchange-format.md). Beim Speichern sendet die App genau einen Build oder ein Team per `POST` im Modus `merge` an dieselbe Adresse; alles andere bleibt unberührt, und der Server begrenzt die Werte wie bei den anderen Clients. Teams löscht sie mit `DELETE api/teams.php?name=…`; Umbenennen ist Speichern unter dem neuen Namen plus Löschen des alten. Die Teilnamen der Artefakte stammen aus `stammdaten.json` (`pieces` je Set). Die Auswahllisten (Haupt- und Substats) stehen in `data/BuildEditing.kt` und müssen mit `web/index.html` und `desktop/widgets.py` übereinstimmen.

## Bauen und Testen

```sh
cd android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # einmalig, nicht eingecheckt
./gradlew :app:testDebugUnitTest      # Tests gegen shared/testdata und shared/stammdaten.json
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

Oder den Ordner `android/` in Android Studio öffnen.

Im Debug-Build ist Klartext-HTTP zu `10.0.2.2` und `localhost` erlaubt, um im Emulator gegen einen lokalen PHP-Server (`php -S 127.0.0.1:8000`) zu testen; als Server-Adresse dann `http://10.0.2.2:8000`. Release-Builds erlauben nur HTTPS.

## Release-Build

**Einmalig: Signierschlüssel anlegen** (die Dateien stehen in `.gitignore`)

```sh
cd android
keytool -genkeypair -keystore release.keystore -alias genshin-teams -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties   # Passwörter eintragen
```

**Bauen**

```sh
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk  (zum direkten Installieren)
./gradlew :app:bundleRelease      # app/build/outputs/bundle/release/app-release.aab (für den Play Store)
```

Der Release-Build ist mit R8 verkleinert (ca. 15 MB statt 28 MB), nicht debuggbar und erlaubt nur HTTPS. Ohne `keystore.properties` entsteht eine unsignierte APK.

**Wichtig**
- `release.keystore` und `keystore.properties` sichern (z. B. Passwortmanager). Ohne den Schlüssel lässt sich die App nicht mehr aktualisieren, nur neu installieren.
- **Schlüssel wiederherstellen** (neuer Rechner): Repo klonen, `release.keystore` und `keystore.properties` aus der Sicherung nach `android/` kopieren, mit `keytool -list -v -keystore android/release.keystore` prüfen und wie oben bauen. Der SHA-256-Fingerabdruck der signierten APK muss derselbe sein wie bei der installierten App.
- Debug- und Release-Build haben denselben Paketnamen, aber verschiedene Schlüssel. Beim Wechsel die andere Version zuerst deinstallieren.
- Versionen in `app/build.gradle.kts` hochzählen (`versionCode` bei jeder Auslieferung, `versionName` nach Belieben).
- Das Icon wurde aus `shared/assets/images/paimon.png` erzeugt (`res/drawable-nodpi/ic_launcher_foreground.png`).

**Testen mit R8:** `./gradlew :app:assembleStaging` baut dieselbe verkleinerte App (Paketname `…staging`, läuft parallel zur echten), erlaubt aber HTTP zu `10.0.2.2` für einen lokalen Server. Damit lässt sich prüfen, dass R8 nichts Nötiges entfernt hat (JSON lesen und schreiben, Anmeldung).

## Nächste Schritte

- Änderungen offline vormerken und später abgleichen (braucht eine Konfliktlösung, siehe Offline-Cache)
- Passwort ändern (dafür fehlt ein API-Endpunkt)

## Builds und Signierung

Gebaut wird mit dem mitgelieferten Wrapper: `./gradlew :app:assembleDebug` (Debug-APK), `:app:assembleStaging` (wie Release mit R8, aber Debug-Schlüssel, läuft parallel zur echten App) und `:app:testDebugUnitTest` (Tests gegen `shared/stammdaten.json` und das Austauschformat). Ein Release braucht einen eigenen Signierschlüssel: Vorlage `keystore.properties.example` nach `keystore.properties` kopieren und ausfüllen (nicht im Git); ohne diese Datei entsteht eine unsignierte Release-APK. Der Schlüssel der Genshin-App gehört nicht in dieses Projekt.

## Inhalt der Builds

Pro Charakter: Level, Eidolon (E0–E6), Talente (Basis bis 7, Fertigkeit, Ultimate und Talent bis 12), Lichtkegel mit Level und Überlagerung (S1–S5) und sechs Relikte (Kopf, Hände, Körper, Füße, Planarsphäre, Verbindungsseil) mit Set, Seltenheit, Level, Hauptwert und vier Substats. Relikt-Sets und Planar-Ornamente werden je nach Slot getrennt angeboten, die aktiven 2er-/4er-Boni berechnet die App aus den Slots. Das Format der Builds ist identisch zu Web und Desktop (`star-rail-team-manager`, Version 1).
