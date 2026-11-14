#!/usr/bin/env bash
# Lädt die Web-Version (web/ plus shared/assets) per FTPS auf den Server.
#
#   deploy/deploy.sh --dry-run    zeigt nur, was sich ändern würde
#   deploy/deploy.sh              lädt Geändertes hoch
#
# - Zugangsdaten stehen in deploy/deploy.local.env (nicht im Git, Vorlage: deploy/deploy.example.env).
# - Hochgeladen wird nur, was fehlt oder sich unterscheidet. Dateien auf dem Server, die es lokal nicht
#   gibt, bleiben unberührt (es wird nie etwas gelöscht).
# - Vor dem Überschreiben landet die Serverdatei in deploy/backup/<Zeitstempel>/.
# - Bewusst NIE hochgeladen: api/config.local.php und api/admins.local.php (Datenbank-Zugangsdaten, wird einmalig auf dem Server angelegt).
#   api/db.php enthält keine Zugangsdaten und wird mit hochgeladen.
set -euo pipefail
cd "$(dirname "$0")/.."

DRY=0
case "${1:-}" in --dry-run) DRY=1 ;; "") ;; *) echo "Unbekannte Option: $1"; exit 2 ;; esac
[ -f deploy/deploy.local.env ] || { echo "deploy/deploy.local.env fehlt (Vorlage: deploy/deploy.example.env)"; exit 1; }
set -a; . deploy/deploy.local.env; set +a

umask 077
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
printf 'machine %s login %s password %s\n' "$FTP_HOST" "$FTP_USER" "$FTP_PASS" > "$TMP/netrc"   # Passwort nicht in der Prozessliste
BASE="ftp://$FTP_HOST${FTP_DIR:+/$FTP_DIR}"
BACKUP="deploy/backup/$(date +%Y%m%d-%H%M%S)"

ftp() { curl -sS --max-time 180 --ssl-reqd --netrc-file "$TMP/netrc" "$@"; }
enc() { python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$1"; }

# Dateien im Server-Ordner: "Größe<TAB>Pfad" je Zeile
remote_list() {
    local dir="$1"
    ftp "$BASE/${dir:+$(enc "$dir")/}" | awk -v d="$dir" '$1 ~ /^-/ { n = $9; for (i = 10; i <= NF; i++) n = n " " $i; print $5 "\t" (d == "" ? n : d "/" n) }'
}

# Lokale Dateien: "Quelle<TAB>Zielpfad auf dem Server"
local_files() {
    printf 'web/index.html\tindex.html\n'
    for f in web/api/*.php; do
        case "$(basename "$f")" in config.local.php | admins.local.php | config.example.php) continue ;; esac
        printf '%s\tapi/%s\n' "$f" "$(basename "$f")"
    done
    for f in shared/assets/images/*; do printf '%s\tassets/images/%s\n' "$f" "$(basename "$f")"; done
}

echo "Server: $FTP_HOST/${FTP_DIR:-}  ($([ $DRY = 1 ] && echo Probelauf || echo Upload))"
declare -A REMOTE_SIZE
for dir in "" api assets assets/images; do
    while IFS=$'\t' read -r size path; do REMOTE_SIZE["$path"]=$size; done < <(remote_list "$dir")
done

new=0 changed=0 same=0
while IFS=$'\t' read -r src dst; do
    local_size=$(stat -c %s "$src")
    state=same
    if [ -z "${REMOTE_SIZE[$dst]+x}" ]; then
        state=new
    elif [ "${REMOTE_SIZE[$dst]}" != "$local_size" ]; then
        state=changed
    else
        case "$dst" in *.php | *.html)      # Textdateien: gleiche Größe reicht nicht, Inhalt vergleichen
            ftp -o "$TMP/cmp" "$BASE/$(enc "$dst")"
            cmp -s "$TMP/cmp" "$src" || state=changed ;;
        esac
    fi
    case $state in
        same) same=$((same + 1)); continue ;;
        new) new=$((new + 1)); echo "  neu          $dst" ;;
        changed) changed=$((changed + 1)); echo "  geändert     $dst" ;;
    esac
    [ $DRY = 1 ] && continue
    if [ $state = changed ]; then      # Serverstand sichern, bevor er überschrieben wird
        mkdir -p "$BACKUP/$(dirname "$dst")"
        ftp -o "$BACKUP/$dst" "$BASE/$(enc "$dst")"
    fi
    ftp --ftp-create-dirs -T "$src" "$BASE/$(enc "$dst")"
done < <(local_files)

echo "neu: $new, geändert: $changed, unverändert: $same"
[ $DRY = 1 ] && exit 0
[ $((new + changed)) -gt 0 ] && [ -d "$BACKUP" ] && echo "Sicherung der überschriebenen Dateien: $BACKUP"
if [ -n "${SITE_URL:-}" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' "$SITE_URL/")
    echo "Startseite $SITE_URL: HTTP $code"
    [ "$code" = 200 ] || { echo "ACHTUNG: Startseite antwortet nicht mit 200"; exit 1; }
fi
