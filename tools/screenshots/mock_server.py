#!/usr/bin/env python3
"""Mock-API für die Android-Screenshots (Port 8766; im Emulator als http://10.0.2.2:8766/). Bei change_password antwortet er mit „falsch“."""
import json, os, sys, http.server
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from demo_data import export_file, rows
exp = export_file()
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): print(self.command, self.path, flush=True)
    def send(self, o, code=200):
        b = json.dumps(o, ensure_ascii=False).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0) or 0); body = self.rfile.read(n)
        if "action=token" in self.path: return self.send({"token": "demo-token", "username": "demo"})
        if "action=change_password" in self.path: return self.send({"error": "Das aktuelle Passwort ist falsch."}, 403)
        return self.send({"success": True, "builds": 0, "teams": 0, "skipped": []})
    def do_GET(self):
        p = self.path.split("?")[0].split("/")[-1]
        if p == "auth.php": return self.send({"logged_in": True, "username": "demo", "user_id": 1, "is_admin": False})
        if p == "characters.php": return self.send(rows("characters"))
        if p == "light_cones.php": return self.send(rows("light_cones"))
        if p == "relic_sets.php": return self.send(rows("relic_sets"))
        if p == "transfer.php": return self.send(exp)
        return self.send({})
http.server.ThreadingHTTPServer(("0.0.0.0", 8766), H).serve_forever()
