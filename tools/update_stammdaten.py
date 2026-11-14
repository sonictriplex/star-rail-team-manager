#!/usr/bin/env python3
"""Prüft, ob die Star-Rail-Stammdaten (Charaktere, Lichtkegel, Relikt-Sets) aktuell sind, und ergänzt Fehlendes.

Quelle: github.com/Mar-7th/StarRailRes (Daten und Bilder, englische Namen wie in den Stammdaten).

    python3 tools/update_stammdaten.py            Prüfen (ändert nichts, Exit-Code 1 = es fehlt etwas)
    python3 tools/update_stammdaten.py --apply    Fehlendes ergänzen (fragt vorher nach)
    python3 tools/update_stammdaten.py --apply --yes   ohne Rückfrage
    python3 tools/update_stammdaten.py --auto     ALLES in einem Schritt (siehe unten), geeignet für regelmäßige Läufe

Was --apply macht:
  - trägt Neues in shared/stammdaten.json ein (an der passenden Stelle der Sortierung, ohne Umformatierung)
  - lädt die Bilder nach shared/assets/images (vorhandene Dateien werden nie überschrieben)
  - erzeugt web/seed_stammdaten.sql neu (web/tools/make_seed.py)
Ohne --auto danach: web/seed_stammdaten.sql in phpMyAdmin importieren (wiederholbar) und deploy/deploy.sh starten.

--auto erledigt zusätzlich ohne Rückfrage und ohne Handarbeit:
  1. committet die Änderungen lokal (ohne Push; abschaltbar mit --no-commit)
  2. lädt Bilder und Code per deploy/deploy.sh hoch
  3. spielt den Seed über tools/remote_sql.py in die Server-Datenbank ein
  4. prüft über die API, ob alles angekommen ist
Sicherung gegen Fehlläufe: Mehr als --max-new neue Einträge (Standard 15) brechen --auto ab, ohne etwas zu ändern.
Der Lauf ist wiederholbar: ein zweiter Aufruf findet nichts Neues und meldet „Alles aktuell“.

Unbekannte Pfade oder Elemente (z. B. ein neuer Pfad im Spiel) werden nur gemeldet und NICHT eingetragen:
Die Datenbank-Spalten sind ENUMs. Erst web/schema.sql und eine Migration (ALTER TABLE ... MODIFY) ergänzen,
dann PATHS/ELEMENTS unten erweitern, außerdem das Pfad-Icon (path_<name>.png) und die Filter in web/index.html.

Achtung: Die Quelle enthält manchmal Einträge, die im Spiel noch nicht erschienen sind. Die Liste vor dem Ergänzen prüfen.
"""
import argparse
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

RAW = "https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/"
ROOT = Path(__file__).resolve().parents[1]

PATHS = {"Warrior": "Die Zerstörung", "Rogue": "Die Jagd", "Mage": "Die Gelehrsamkeit", "Shaman": "Die Harmonie",
         "Warlock": "Die Nichtigkeit", "Knight": "Die Bewahrung", "Priest": "Der Überfluss", "Memory": "Das Gedenken",
         "Elation": "Das Hochgefühl"}
ELEMENTS = {"Physical": "Physisch", "Fire": "Feuer", "Ice": "Eis", "Thunder": "Blitz", "Wind": "Wind",
            "Quantum": "Quantum", "Imaginary": "Imaginär"}


def fetch_json(name):
    with urllib.request.urlopen(RAW + "index_new/en/" + name, timeout=90) as r:
        return json.load(r)


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def download(source, target):
    if target.exists():
        return "vorhanden"
    req = urllib.request.Request(RAW + source, headers={"User-Agent": "stammdaten-update"})
    with urllib.request.urlopen(req, timeout=60) as r:
        target.write_bytes(r.read())
    return "geladen"


def find_new(data):
    chars_src, lcs_src, sets_src = fetch_json("characters.json"), fetch_json("light_cones.json"), fetch_json("relic_sets.json")
    have = {k: {n["name"].casefold() for n in data[k]} for k in ("characters", "light_cones", "relic_sets")}
    new = {"characters": [], "light_cones": [], "relic_sets": []}
    skipped = []

    seen = set()
    # Gleiche Namen in der Quelle (z. B. zwei "March 7th" mit verschiedenem Pfad): der mit der kleinsten ID behält den Namen,
    # die übrigen bekommen den Pfad als Zusatz ("March 7th (Die Jagd)"), weil der Name in der Datenbank eindeutig sein muss
    first_id = {}
    for cid, c in sorted(chars_src.items(), key=lambda kv: int(kv[0])):
        first_id.setdefault(c["name"], cid)
    for cid, c in chars_src.items():
        path, element = PATHS.get(c["path"]), ELEMENTS.get(c["element"])
        name = c["name"]
        if name != "{NICKNAME}" and first_id[name] != cid and path:
            name = f"{name} ({path})"
        if name == "{NICKNAME}":                       # Trailblazer: je Pfad ein Eintrag (Varianten teilen sich ihn)
            name = f"Trailblazer ({re.sub(r'^(Die|Der|Das)\s+', '', path)})" if path else name   # Name ohne Artikel, bleibt stabil
        if name.casefold() in have["characters"] or name.casefold() in seen:
            continue
        seen.add(name.casefold())
        if not path or not element:
            skipped.append(f"Charakter {name}: unbekannter Pfad „{c['path']}“ oder Element „{c['element']}“")
            continue
        new["characters"].append({"name": name, "element": element, "path": path, "rarity": c["rarity"],
                                  "icon_path": f"assets/images/char_{slug(name)}.png", "_src": f"icon/avatar/{cid}.png"})

    for lid, l in lcs_src.items():
        if l["name"].casefold() in have["light_cones"]:
            continue
        path = PATHS.get(l["path"])
        if not path:
            skipped.append(f"Lichtkegel {l['name']}: unbekannter Pfad „{l['path']}“")
            continue
        new["light_cones"].append({"name": l["name"], "path": path, "rarity": l["rarity"],
                                   "icon_path": f"assets/images/lc_{slug(l['name'])}.png", "_src": l["icon"]})

    for sid, s in sets_src.items():
        if s["name"].casefold() in have["relic_sets"]:
            continue
        desc = s.get("desc") or []
        new["relic_sets"].append({"name": s["name"], "kind": "Ornament" if int(sid) >= 300 else "Relikt",
                                  "bonus_2pc": desc[0] if desc else None, "bonus_4pc": desc[1] if len(desc) > 1 else None,
                                  "icon_path": f"assets/images/relic_{slug(s['name'])}.png", "_src": s["icon"]})
    return new, skipped


SORT_KEYS = {"characters": lambda x: x["name"], "light_cones": lambda x: x["name"],
             "relic_sets": lambda x: (x["kind"], x["name"])}


def insert_sorted(items, entry, key):
    i = next((k for k, x in enumerate(items) if key(x) > key(entry)), len(items))
    items.insert(i, entry)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apply", action="store_true", help="Fehlendes ergänzen (Standard: nur prüfen)")
    ap.add_argument("--yes", action="store_true", help="bei --apply nicht nachfragen")
    ap.add_argument("--auto", action="store_true", help="ergänzen, committen, deployen, Datenbank aktualisieren, prüfen")
    ap.add_argument("--max-new", type=int, default=15, help="bei --auto: Abbruch bei mehr neuen Einträgen (Standard 15)")
    ap.add_argument("--no-commit", action="store_true", help="bei --auto: nicht committen")
    args = ap.parse_args()
    if args.auto:
        args.apply = args.yes = True

    path = ROOT / "shared" / "stammdaten.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    print("Lade Daten von StarRailRes …")
    new, skipped = find_new(data)
    print(f"Stand: {len(data['characters'])} Charaktere, {len(data['light_cones'])} Lichtkegel, {len(data['relic_sets'])} Relikt-Sets.\n")

    labels = {"characters": "Neue Charaktere", "light_cones": "Neue Lichtkegel", "relic_sets": "Neue Relikt-Sets"}
    for key, label in labels.items():
        print(f"{label}: {len(new[key])}")
        for e in new[key]:
            extra = {"characters": f"{e.get('rarity')}★ {e.get('element')}, {e.get('path')}",
                     "light_cones": f"{e.get('rarity')}★ {e.get('path')}",
                     "relic_sets": e.get("kind", "")}[key]
            print(f"  + {e['name']:<45} {extra}")
    if skipped:
        print(f"\nNicht eintragbar ({len(skipped)}), siehe Hinweis im Skript-Kopf:")
        for s in skipped:
            print(f"  ! {s}")

    total = sum(len(v) for v in new.values())
    if not total and not skipped:
        print("\nAlles aktuell.")
        return 0
    if not args.apply:
        print("\nNichts geändert (Prüfmodus). Zum Ergänzen: --apply")
        return 1
    if not total:
        print("\nNichts zu ergänzen.")
        return 1
    if args.auto and total > args.max_new:
        print(f"\nABBRUCH: {total} neue Einträge, erlaubt sind bei --auto höchstens {args.max_new}. "
              "Liste prüfen und mit --apply (oder --max-new) bewusst fortfahren. Nichts wurde verändert.")
        return 1
    if not args.yes and input("\nDiese Einträge ergänzen? Einträge, die im Spiel noch nicht erschienen sind, vorher prüfen. [j/N] ").strip().lower() not in ("j", "ja", "y"):
        print("Abgebrochen.")
        return 1

    images = ROOT / "shared" / "assets" / "images"
    images.mkdir(parents=True, exist_ok=True)
    for key, entries in new.items():
        for e in entries:
            target = images / Path(e["icon_path"]).name
            try:
                state = download(e.pop("_src"), target)
            except Exception as ex:
                state = f"FEHLER ({ex})"
            if state != "vorhanden":
                print(f"  Bild {target.name}: {state}")
            insert_sorted(data[key], e, SORT_KEYS[key])

    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nErgänzt: {total} Einträge in shared/stammdaten.json.")

    seed = ROOT / "web" / "tools" / "make_seed.py"
    if seed.exists():
        subprocess.run([sys.executable, str(seed)], check=True)
    if not args.auto:
        print("Nächste Schritte: web/seed_stammdaten.sql in phpMyAdmin importieren (wiederholbar), dann deploy/deploy.sh.")
        return 1 if skipped else 0
    return publish(new, args, skipped)


def run(cmd):
    return subprocess.run(cmd, cwd=ROOT).returncode == 0


def publish(new, args, skipped):
    """--auto: committen, deployen, Datenbank aktualisieren, per API prüfen."""
    print("\n== 1/4 Commit ==")
    if args.no_commit or not (ROOT / ".git").exists():
        print("  übersprungen")
    else:
        names = ", ".join(e["name"] for v in new.values() for e in v)
        paths = ["shared/stammdaten.json", "shared/assets/images", "web/seed_stammdaten.sql"]
        run(["git", "add", *paths])
        msg = f"Stammdaten aktualisiert: {names}"[:200]
        print("  " + ("committet" if run(["git", "commit", "-q", "-m", msg, "--", *paths]) else "nichts zu committen"))

    print("\n== 2/4 Deploy (Bilder und Code) ==")
    if not run([str(ROOT / "deploy" / "deploy.sh")]):
        print("FEHLER beim Deploy. Die Datenbank wurde NICHT aktualisiert.")
        return 1

    print("\n== 3/4 Datenbank ==")
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import remote_sql
    try:
        remote_sql.run_sql((ROOT / "web" / "seed_stammdaten.sql").read_text(encoding="utf-8"))
    except (RuntimeError, SystemExit) as e:
        print(f"FEHLER: {e}")
        return 1

    print("\n== 4/4 Kontrolle über die API ==")
    base = remote_sql.load_env()["SITE_URL"].rstrip("/")
    endpoints = {"characters": "characters.php", "light_cones": "light_cones.php", "relic_sets": "relic_sets.php"}
    missing = []
    for key, endpoint in endpoints.items():
        if not new[key]:
            continue
        try:
            req = urllib.request.Request(f"{base}/api/{endpoint}", headers={"User-Agent": "stammdaten-update"})
            with urllib.request.urlopen(req, timeout=60) as r:
                online = {x["name"] for x in json.load(r)}
        except Exception as e:
            print(f"FEHLER bei der Kontrolle von {endpoint}: {e}")
            return 1
        missing += [e["name"] for e in new[key] if e["name"] not in online]
    if missing:
        print("FEHLT online: " + ", ".join(missing))
        return 1
    print("  alle neuen Einträge sind online.")
    return 1 if skipped else 0


if __name__ == "__main__":
    sys.exit(main())
