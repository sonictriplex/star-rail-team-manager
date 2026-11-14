#!/usr/bin/env python3
"""Führt eine SQL-Datei (INSERT/UPDATE/DELETE) auf der Datenbank des Servers aus, ohne phpMyAdmin.

    python3 tools/remote_sql.py web/migrations/003_scholar_set_name.sql

Die Datenbank ist nur vom Server aus erreichbar. Das Skript lädt deshalb per FTPS eine einmalige PHP-Datei
(api/_sql_<zufall>.php) hoch, ruft sie per HTTPS auf und löscht sie wieder:
  - Die Datei enthält nur den SHA-256-Hash eines Zufalls-Tokens (256 Bit), das Token selbst steht nur im Aufruf.
  - Sie benutzt die vorhandene api/db.php (die Zugangsdaten bleiben auf dem Server).
  - Alle Anweisungen laufen in EINER Transaktion; bei einem Fehler wird nichts verändert.
  - Sie löscht sich nach dem Aufruf selbst; das Skript prüft das und löscht sonst per FTP nach.
Erlaubt sind nur INSERT, UPDATE, DELETE und REPLACE (kein DROP/ALTER; SELECT und Kommentare werden übersprungen).
Zugangsdaten: deploy/deploy.local.env (wie bei deploy/deploy.sh).
"""
import base64
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALLOWED = ("INSERT", "UPDATE", "DELETE", "REPLACE")


def load_env():
    env = {}
    path = ROOT / "deploy" / "deploy.local.env"
    if not path.exists():
        raise SystemExit("deploy/deploy.local.env fehlt (Vorlage: deploy/deploy.example.env)")
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s*([A-Z_]+)\s*=\s*(.*)$", line)
        if m and not line.lstrip().startswith("#"):
            v = m.group(2).strip()
            if len(v) >= 2 and v[0] == v[-1] and v[0] in "'\"":
                v = v[1:-1]
            env[m.group(1)] = v
    for key in ("FTP_HOST", "FTP_USER", "FTP_PASS", "SITE_URL"):
        if not env.get(key):
            raise SystemExit(f"{key} fehlt in deploy/deploy.local.env")
    return env


def split_statements(sql):
    """Zerlegt SQL in Anweisungen (beachtet Zeichenketten und -- Kommentare). Gibt nur erlaubte zurück."""
    statements, buf, i, n = [], [], 0, len(sql)
    quote = None
    while i < n:
        ch = sql[i]
        if quote:
            buf.append(ch)
            if ch == "\\" and i + 1 < n:
                buf.append(sql[i + 1]); i += 1
            elif ch == quote:
                if i + 1 < n and sql[i + 1] == quote:       # verdoppeltes Anführungszeichen
                    buf.append(sql[i + 1]); i += 1
                else:
                    quote = None
        elif ch in "'\"`":
            quote = ch; buf.append(ch)
        elif ch == "-" and sql.startswith("--", i) and (i + 2 >= n or sql[i + 2] in " \t\r\n"):
            while i < n and sql[i] != "\n":
                i += 1
            continue
        elif ch == ";":
            statements.append("".join(buf).strip()); buf = []
        else:
            buf.append(ch)
        i += 1
    statements.append("".join(buf).strip())

    result = []
    for s in (x for x in statements if x):
        word = s.split(None, 1)[0].upper()
        if word == "SELECT" or re.match(r"SET\s+NAMES\s+utf8mb4\s*$", s, re.I):   # db.php stellt utf8mb4 schon ein
            continue
        if word not in ALLOWED:
            raise SystemExit(f"Nicht erlaubte Anweisung ({word}): {s[:60]} …")
        result.append(s)
    return result


class Ftp:
    def __init__(self, env):
        self.env = env
        self.tmp = tempfile.TemporaryDirectory()
        netrc = Path(self.tmp.name) / "netrc"
        old = os.umask(0o077)
        netrc.write_text(f"machine {env['FTP_HOST']} login {env['FTP_USER']} password {env['FTP_PASS']}\n")
        os.umask(old)
        self.netrc = str(netrc)
        d = env.get("FTP_DIR", "").strip("/")
        self.dir = d
        self.base = f"ftp://{env['FTP_HOST']}" + (f"/{d}" if d else "")

    def _curl(self, *args):
        return subprocess.run(["curl", "-sS", "--max-time", "120", "--ssl-reqd", "--netrc-file", self.netrc, *args],
                              capture_output=True, text=True)

    def upload(self, local, remote):
        r = self._curl("--ftp-create-dirs", "-T", str(local), f"{self.base}/{remote}")
        if r.returncode:
            raise RuntimeError("FTP-Upload fehlgeschlagen: " + r.stderr.strip())

    def delete(self, remote):
        path = f"{self.dir}/{remote}" if self.dir else remote
        self._curl("-Q", f"-DELE {path}", f"ftp://{self.env['FTP_HOST']}/")


PHP = r"""<?php
// Einmalige Datei von tools/remote_sql.py: löscht sich nach dem Aufruf selbst.
ignore_user_abort(true);
$hash = '__HASH__';
$token = $_SERVER['HTTP_X_UPDATE_TOKEN'] ?? '';
if (!hash_equals($hash, hash('sha256', $token))) { http_response_code(404); exit; }
@unlink(__FILE__);
header('Content-Type: application/json; charset=utf-8');
require __DIR__ . '/db.php';
$statements = json_decode(base64_decode('__PAYLOAD__'), true);
$rows = [];
try {
    $pdo->beginTransaction();
    foreach ($statements as $s) { $rows[] = $pdo->exec($s); }
    $pdo->commit();
    echo json_encode(["ok" => true, "statements" => count($statements), "rows" => $rows]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    http_response_code(500);
    echo json_encode(["ok" => false, "error" => $e->getMessage()]);
}
"""


def run_sql(sql, quiet=False):
    """Führt die Anweisungen auf dem Server aus. Gibt die Zahl der Anweisungen zurück; wirft bei Fehlern."""
    statements = split_statements(sql)
    if not statements:
        return 0
    env = load_env()
    ftp = Ftp(env)
    token = secrets.token_urlsafe(32)
    name = f"_sql_{secrets.token_hex(8)}.php"
    php = (PHP.replace("__HASH__", hashlib.sha256(token.encode()).hexdigest())
              .replace("__PAYLOAD__", base64.b64encode(json.dumps(statements).encode()).decode()))
    local = Path(ftp.tmp.name) / name
    local.write_text(php, encoding="utf-8")
    url = env["SITE_URL"].rstrip("/") + "/api/" + name

    ftp.upload(local, f"api/{name}")
    try:
        req = urllib.request.Request(url, headers={"X-Update-Token": token, "User-Agent": "remote-sql"})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                body = json.load(r)
        except urllib.error.HTTPError as e:
            try:
                body = json.load(e)
            except Exception:
                body = {"ok": False, "error": f"HTTP {e.code}"}
        if not body.get("ok"):
            raise RuntimeError("Datenbankfehler (nichts wurde verändert): " + str(body.get("error")))
        if not quiet:
            print(f"  Datenbank: {body['statements']} Anweisungen ausgeführt, {sum(x or 0 for x in body['rows'])} Zeilen betroffen.")
        return body["statements"]
    finally:
        # Die Datei löscht sich selbst; sicherheitshalber per FTP-Liste prüfen und notfalls entfernen
        listing = ftp._curl(f"{ftp.base}/api/")
        if name in listing.stdout or listing.returncode:
            ftp.delete(f"api/{name}")
            if not quiet:
                print("  Hinweis: Einmal-Datei per FTP gelöscht.")
        ftp.tmp.cleanup()


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    path = Path(sys.argv[1])
    if not path.is_file():
        raise SystemExit(f"Datei nicht gefunden: {path}")
    count = run_sql(path.read_text(encoding="utf-8"))
    if not count:
        print("Keine ausführbaren Anweisungen in der Datei.")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        sys.exit(str(e))
