#!/usr/bin/env python3
"""Web-Screenshots nach docs/screenshots/web/ (Playwright, API gemockt, 1440x1029 auf 1008 Breite verkleinert).

Aufruf:  python3 tools/screenshots/web.py   (Voraussetzung: pip install playwright pillow; playwright install chromium)"""
import functools, http.server, json, threading
from pathlib import Path
from playwright.sync_api import sync_playwright
from PIL import Image
from demo_data import ROOT, TEAMS, rows

OUT = ROOT / "docs/screenshots/web"
state = {"login": True}
def handler(route, req):
    u = req.url; p = u.split("/api/")[1].split("?")[0]
    def j(o, status=200): route.fulfill(status=status, content_type="application/json", body=json.dumps(o, ensure_ascii=False))
    if p=="auth.php":
        if "action=status" in u: return j({"logged_in":state["login"],"username":"demo","user_id":1,"is_admin":True} if state["login"] else {"logged_in":False})
        return j({"success":True})
    if p=="characters.php": return j(rows("characters"))
    if p=="light_cones.php": return j(rows("light_cones"))
    if p=="relic_sets.php": return j(rows("relic_sets"))
    if p=="teams.php": return j(TEAMS)
    if p=="stammdaten.php": return j({"up_to_date":True,"characters":0,"light_cones":0,"relic_sets":0})
    return j({})
class Q(http.server.SimpleHTTPRequestHandler):
    def log_message(self,*a): pass
srv = http.server.ThreadingHTTPServer(("127.0.0.1",8765), functools.partial(Q, directory=str(ROOT/"web")))
threading.Thread(target=srv.serve_forever, daemon=True).start()
from PIL import Image
def save(page, name):
    p = str(OUT/name); page.screenshot(path=p, type="jpeg", quality=88)
    im = Image.open(p)
    if im.width > 1100: im.resize((1008, round(im.height*1008/im.width)), Image.LANCZOS).save(p, quality=88)
with sync_playwright() as pw:
    b = pw.chromium.launch()
    pg = b.new_page(viewport={"width":1000,"height":700}); pg.route("**/api/**", handler)
    # Anmeldung
    state["login"]=False; pg.goto("http://127.0.0.1:8765/"); pg.wait_for_timeout(1200)
    pg.fill("#authUsername","demo"); pg.fill("#authPassword","geheimes-passwort"); save(pg,"login.jpg")
    state["login"]=True; pg.reload(); pg.wait_for_timeout(1500)
    save(pg,"overview.jpg")
    pg.click("#userMenuButton"); pg.wait_for_timeout(300); save(pg,"menu.jpg")
    pg.click("#changePasswordBtn"); pg.wait_for_timeout(300)
    pg.fill("#cpOld","altes-passwort"); pg.fill("#cpNew","neues-passwort-1"); pg.fill("#cpRepeat","neues-passwort-1"); save(pg,"password.jpg")
    pg.evaluate("closeChangePassword()")
    pg.click("#userMenuButton"); pg.click("#deleteAccountBtn"); pg.wait_for_timeout(300); save(pg,"delete-account.jpg")
    pg.evaluate("closeDeleteAccount()")
    pg.click("#userMenuButton"); pg.click("#updateDataBtn"); pg.wait_for_timeout(600); save(pg,"update.jpg")
    pg.evaluate("closeUpdateData()")
    pg.click("#userMenuButton"); pg.click("#aboutBtn"); pg.wait_for_timeout(300); save(pg,"about.jpg")
    pg.evaluate("closeAbout()")
    pg.close()
    pg = b.new_page(viewport={"width":1440,"height":1029}); pg.route("**/api/**", handler)
    pg.goto("http://127.0.0.1:8765/"); pg.wait_for_timeout(1500)
    pg.select_option("#teamsSelect","Blitz-Team"); pg.wait_for_timeout(800); save(pg,"team.jpg")
    pg.screenshot(path="/dev/null") if False else None
    pg.evaluate("openCharModal()"); pg.wait_for_timeout(500); pg.fill("#charSearch","a"); pg.wait_for_timeout(300); save(pg,"character-search.jpg")
    pg.evaluate("closeCharModal()")
    pg.evaluate("toggleDetailView(0)"); pg.wait_for_timeout(800); save(pg,"build.jpg")
    b.close()
