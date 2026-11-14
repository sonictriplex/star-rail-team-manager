#!/usr/bin/env python3
"""Erzeugt web/seed_stammdaten.sql aus shared/stammdaten.json (Charaktere, Lichtkegel, Relikt-Sets).

Aufruf im Projektordner:  python3 web/tools/make_seed.py
Die Datei ist wiederholbar einspielbar (ON DUPLICATE KEY UPDATE)."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
data = json.loads((root / "shared" / "stammdaten.json").read_text(encoding="utf-8"))

def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

def block(table, cols, rows):
    updates = ", ".join(f"{c}=VALUES({c})" for c in cols if c != "name")
    values = ",\n".join("  (" + ", ".join(q(r.get(c)) for c in cols) + ")" for r in rows)
    return f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n{values}\nON DUPLICATE KEY UPDATE {updates};\n"

out = ["-- Erzeugt von web/tools/make_seed.py aus shared/stammdaten.json. Nicht von Hand ändern.",
       "SET NAMES utf8mb4;", ""]
out.append(block("characters", ["name", "element", "path", "rarity", "icon_path"], data["characters"]))
for lc in data["light_cones"]:   # Fähigkeit als JSON-Text in der Spalte passive_json
    lc["passive_json"] = json.dumps(lc["passive"], ensure_ascii=False) if lc.get("passive") else None
out.append(block("light_cones", ["name", "path", "rarity", "icon_path", "passive_json"], data["light_cones"]))
for rs in data["relic_sets"]:   # Teile-Namen als JSON-Text in der Spalte pieces_json
    rs["pieces_json"] = json.dumps(rs["pieces"], ensure_ascii=False) if rs.get("pieces") else None
out.append(block("relic_sets", ["name", "kind", "bonus_2pc", "bonus_4pc", "icon_path", "pieces_json"], data["relic_sets"]))
(root / "web" / "seed_stammdaten.sql").write_text("\n".join(out), encoding="utf-8")
print("web/seed_stammdaten.sql geschrieben:", {k: len(v) for k, v in data.items()})

# Alte englische Namen -> deutsche Namen (Import alter Export-Dateien, ältere App-Versionen), als PHP-Tabelle für api/helpers.php
aliases = json.loads((root / "shared" / "name_aliases.json").read_text(encoding="utf-8"))
aliases.pop("_hinweis", None)
def php(v):
    return "'" + v.replace("\\", "\\\\").replace("'", "\\'") + "'"
lines = ["<?php", "// Erzeugt von web/tools/make_seed.py aus shared/name_aliases.json. Nicht von Hand ändern.", "return ["]
for group, pairs in aliases.items():
    lines.append(f"    {php(group)} => [")
    lines += [f"        {php(k)} => {php(v)}," for k, v in pairs.items()]
    lines.append("    ],")
lines.append("];")
(root / "web" / "api" / "name_aliases.php").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("web/api/name_aliases.php geschrieben")
