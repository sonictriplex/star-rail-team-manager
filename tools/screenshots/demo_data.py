"""Gemeinsame Demo-Daten für alle Screenshot-Skripte (Kafka, Acheron, Ruan Mei, Fu Xuan in zwei Teams)."""
import json
from pathlib import Path
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
data = json.loads((ROOT/"shared/stammdaten.json").read_text())
chars = {c["name"]: c for c in data["characters"]}
sets = {s["name"]: s for s in data["relic_sets"]}
SLOTS = ["Kopf","Hände","Körper","Füße","Planarsphäre","Verbindungsseil"]
MAIN = {"Kopf":("LP (Fix)","705.6"),"Hände":("ANG (Fix)","352.8"),"Körper":("KT%","64.8"),"Füße":("Initiative","25"),
        "Planarsphäre":("Blitz-SCH%","38.8"),"Verbindungsseil":("ANG%","43.2")}
SUBS = [("KT%","5.2"),("KSCH%","10.4"),("Effekttreffer%","7.8"),("ANG (Fix)","33")]
def build(name, ev, lc, rs, orn, tal=(6,10,10,10)):
    relics = {}
    for i, sl in enumerate(SLOTS):
        s = rs if i < 4 else orn
        piece = sets[s]["pieces"][sl][0]
        subs = [x for x in [("KT%","5.2"),("KSCH%","10.4"),("Effekttreffer%","7.8"),("ANG%","5.4"),("Initiative","2.3"),("LP%","6.2")] if x[0] != MAIN[sl][0]][:4]
        relics[sl] = {"set": s, "piece": piece, "rarity": 5, "level": 15, "main": MAIN[sl][0], "main_value": MAIN[sl][1],
                      "substats": [{"stat": a, "val": b} for a, b in subs]}
    return {"name": name, "level": 80, "eidolon": ev, "light_cone": lc, "lc_level": 80, "lc_superimpose": 1,
            "relic_set": rs, "relic_set_2": "", "ornament_set": orn, "relics": relics,
            "talents": dict(zip(["basic","skill","ult","talent"], tal))}
def member(name, *a):
    c = chars[name]
    return {"character": {k: c[k] for k in ("name","element","path","rarity","icon_path")}, "saved": build(name, *a)}
K = member("Kafka",1,"Entlang am vorbeiziehenden Ufer","Band des knisternden Donners","Traumwebendes Feenreich")
A = member("Acheron",0,"Entlang am vorbeiziehenden Ufer","Pioniertaucherin toter Gewässer","Amphoreus, Land der Ewigkeit")
R = member("Ruan Mei",2,"Flug zurück zur Erde","Bote im Hackerspace","Amphoreus, Land der Ewigkeit")
F = member("Fu Xuan",0,"Ungerechtes Schicksal","Langlebige Jüngerin","Belobog der Architekten")
TEAMS = {"Blitz-Team":[K,A,R,F],"Test":[K,F]}

def rows(kind):
    """Stammdaten wie die API sie liefert (mit id)."""
    return [dict(r, id=i + 1) for i, r in enumerate(data[kind])]

def export_file():
    """Demo-Builds und -Teams im Austauschformat (shared/exchange-format.md)."""
    return {"format": "star-rail-team-manager", "version": 1, "exported_at": "2026-11-16T12:00:00",
            "builds": [m["saved"] for m in (K, A, R, F)],
            "teams": [{"name": n, "members": [m["character"]["name"] for m in ms]} for n, ms in TEAMS.items()]}
