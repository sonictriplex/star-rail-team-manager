#!/bin/bash
# Android-Screenshots nach docs/screenshots/android/ (nur im EMULATOR, nie auf echten Geräten: dort sind private Benachrichtigungen sichtbar).
#
# Voraussetzungen: Emulator läuft (z. B. Pixel_8, 1080x2400), Debug-APK gebaut (android/gradlew assembleDebug),
# der Mock-Server läuft:  python3 tools/screenshots/mock_server.py &
# Aufruf:  tools/screenshots/android.sh [emulator-5554]
# Die Tipp-Koordinaten gelten für 1080x2400; ändert sich das Layout, müssen sie angepasst werden.
set -euo pipefail
DEV=${1:-emulator-5554}
case "$DEV" in emulator-*) ;; *) echo "Nur Emulator erlaubt, nicht $DEV"; exit 1 ;; esac
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT="$ROOT/docs/screenshots/android"
A() { adb -s "$DEV" "$@"; }
cap() { A exec-out screencap -p | python3 -c "
import sys, io
from PIL import Image
Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB').resize((540, 1200)).save('$OUT/$1.jpg', quality=90)"; }
tap() { A shell input tap "$1" "$2"; }
txt() { A shell input text "$1"; }

A install -r "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
A shell pm clear de.sonictriplex.starrailteams >/dev/null
A shell am start -n de.sonictriplex.starrailteams/.MainActivity >/dev/null; sleep 4
cap login                                              # Anmeldung mit der Standard-Adresse

tap 540 962; A shell input keyevent KEYCODE_MOVE_END   # Server auf den Mock-Server (10.0.2.2) setzen
for _ in 1 2 3 4 5 6; do A shell input keyevent 67 67 67 67 67 67 67 67; done
txt 'http://10.0.2.2:8766/'; tap 540 1163; txt demo; tap 540 1363; txt geheim123
A shell input keyevent 4; sleep 1; tap 540 1530; sleep 4   # Anmelden
cap teams

tap 1006 214; sleep 1; cap menu                        # Menü
A shell input keyevent 4; sleep 1
tap 156 600; sleep 2; cap build                        # Build Kafka
tap 946 214; sleep 2; cap edit                         # Bearbeiten
A shell input keyevent 4; sleep 1; A shell input keyevent 4; sleep 1
tap 1006 214; sleep 1; tap 798 614; sleep 1; cap about # Über
A shell input keyevent 4; sleep 1
tap 1006 214; sleep 1; tap 896 488; sleep 1.5          # Passwort ändern
tap 540 992; sleep 1; txt altes-passwort; sleep 1
tap 540 758; sleep 1; txt neues-passwort-1; sleep 1    # Dialog rutscht mit Tastatur nach oben
tap 540 980; sleep 1; txt neues-passwort-1; sleep 1
A shell input keyevent 4; sleep 1.5; cap password
tap 804 1602; sleep 2; cap password-error              # Server antwortet „Passwort falsch“
