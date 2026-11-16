# Screenshot-Skripte

Erzeugen die Bilder in `docs/screenshots/` (Web, Desktop, Android) mit denselben Demo-Daten (`demo_data.py`: Kafka, Acheron, Ruan Mei, Fu Xuan in „Blitz-Team“ und „Test“, aus `shared/stammdaten.json`). Es wird kein echter Server und kein echtes Konto gebraucht.

| Skript | Ziel | Voraussetzung |
|---|---|---|
| `web.py` | `docs/screenshots/web/` (10 Bilder) | `pip install playwright pillow`, `playwright install chromium` (am besten in einem venv); mockt die API, serviert `web/` selbst |
| `desktop.py` | `docs/screenshots/desktop/` (6 Bilder) | PySide6; läuft offscreen mit temporärer Datenbank |
| `mock_server.py` + `android.sh` | `docs/screenshots/android/` (8 Bilder) | Emulator (Pixel_8, 1080x2400), Debug-APK (`android/gradlew assembleDebug`), pillow |

Android: erst `python3 tools/screenshots/mock_server.py &` starten, dann `tools/screenshots/android.sh`. Das Skript akzeptiert nur Emulatoren (`emulator-…`), auf den privaten Geräten werden keine Screenshots gemacht. Die Tipp-Koordinaten in `android.sh` gelten für 1080x2400 und müssen bei Layoutänderungen angepasst werden. Danach den Mock-Server beenden.

Bei jeder Änderung der Oberfläche oder des Hintergrundbilds Skripte laufen lassen, die Bilder ansehen und mit der Version zusammen committen.
