"""Stammdaten (Charaktere, Lichtkegel, Relikt-Sets) aus/in `shared/stammdaten.json`.

Die Datenbank selbst liegt nicht im Repository. Damit eine neue, leere Datenbank trotzdem
nutzbar ist, werden die Stammdaten aus der versionierten JSON-Datei eingespielt, und zwar
automatisch beim ersten Start (siehe StarRailManager) oder von Hand:

    python seed.py import      # fehlende Einträge in die Datenbank einspielen
    python seed.py export      # Stammdaten aus der Datenbank in die JSON-Datei schreiben

Teams und Builds gehören nicht dazu und bleiben privat in der Datenbank.
"""
import argparse
import json
import os
import sqlite3

from models import BASE_DIR, SHARED_DIR

DB_PATH = os.path.join(BASE_DIR, "star_rail_manager.db")
SEED_PATH = os.path.join(SHARED_DIR, "stammdaten.json")

# Tabelle -> Spalten, die exportiert/importiert werden (die id wird von SQLite vergeben)
TABLES = {
    "characters": ["name", "element", "path", "rarity", "icon_path"],
    "light_cones": ["name", "path", "rarity", "icon_path", "passive_json"],
    "relic_sets": ["name", "kind", "bonus_2pc", "bonus_4pc", "icon_path"],
}


def _row_from_json(table: str, row: dict) -> dict:
    """JSON-Eintrag -> Datenbankzeile (die Fähigkeit eines Lichtkegels wird als Text in `passive_json` abgelegt)."""
    if table == "light_cones":
        row = dict(row)
        row["passive_json"] = json.dumps(row["passive"], ensure_ascii=False) if row.get("passive") else None
    return row


def _row_to_json(table: str, row: dict) -> dict:
    """Datenbankzeile -> JSON-Eintrag (`passive_json` wird wieder zum Objekt `passive`)."""
    if table == "light_cones":
        row = dict(row)
        try:
            row["passive"] = json.loads(row.pop("passive_json", None) or "null")
        except json.JSONDecodeError:
            row["passive"] = None
    return row


def export_seed(conn: sqlite3.Connection, path: str = SEED_PATH) -> dict[str, int]:
    """Schreibt die Stammdaten aus der Datenbank in die JSON-Datei. Rückgabe: Anzahl je Tabelle."""
    data = {}
    for table, columns in TABLES.items():
        rows = conn.execute(f"SELECT {', '.join(columns)} FROM {table} ORDER BY id").fetchall()
        data[table] = [_row_to_json(table, dict(zip(columns, row))) for row in rows]

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return {table: len(rows) for table, rows in data.items()}


def import_seed(conn: sqlite3.Connection, path: str = SEED_PATH) -> dict[str, int]:
    """Spielt fehlende Stammdaten ein. Bestehende Einträge (gleicher Name) bleiben unverändert.

    Rückgabe: Anzahl neu eingefügter Einträge je Tabelle.
    """
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    inserted = {}
    for table, columns in TABLES.items():
        placeholders = ", ".join("?" for _ in columns)
        sql = f"INSERT OR IGNORE INTO {table} ({', '.join(columns)}) VALUES ({placeholders})"
        before = conn.total_changes
        rows = [_row_from_json(table, row) for row in data.get(table, [])]
        conn.executemany(sql, [tuple(row.get(c) for c in columns) for row in rows])
        inserted[table] = conn.total_changes - before
    conn.commit()
    return inserted


def fill_texts(conn: sqlite3.Connection, path: str = SEED_PATH) -> int:
    """Trägt die Fähigkeiten der Lichtkegel und die (deutschen) Set-Boni aus der JSON-Datei nach, auch in
    bestehende Datenbanken. Rückgabe: Anzahl aktualisierter Einträge."""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    changed = 0
    for row in data.get("light_cones", []):
        cur = conn.execute("UPDATE light_cones SET passive_json = ? WHERE name = ?",
                           (json.dumps(row["passive"], ensure_ascii=False) if row.get("passive") else None, row["name"]))
        changed += cur.rowcount
    for row in data.get("relic_sets", []):
        cur = conn.execute("UPDATE relic_sets SET bonus_2pc = ?, bonus_4pc = ? WHERE name = ?",
                           (row.get("bonus_2pc"), row.get("bonus_4pc"), row["name"]))
        changed += cur.rowcount
    return changed


def is_seeded(conn: sqlite3.Connection) -> bool:
    """True, wenn alle Stammdaten-Tabellen mindestens einen Eintrag haben."""
    return all(conn.execute(f"SELECT 1 FROM {table} LIMIT 1").fetchone() for table in TABLES)


def main():
    parser = argparse.ArgumentParser(description="Stammdaten importieren oder exportieren")
    parser.add_argument("action", choices=["import", "export"])
    parser.add_argument("--db", default=DB_PATH, help="Pfad zur Datenbank (Standard: star_rail_manager.db)")
    parser.add_argument("--file", default=SEED_PATH, help="Pfad zur JSON-Datei (Standard: shared/stammdaten.json)")
    args = parser.parse_args()

    if args.action == "import":
        # Schema über den Manager anlegen lassen, falls die Datenbank neu ist
        from manager import StarRailManager
        StarRailManager(args.db, seed_path=None)
        with sqlite3.connect(args.db) as conn:
            counts = import_seed(conn, args.file)
        print("Neu eingespielt:", ", ".join(f"{n} {t}" for t, n in counts.items()))
    else:
        with sqlite3.connect(args.db) as conn:
            counts = export_seed(conn, args.file)
        print(f"Exportiert nach {args.file}:", ", ".join(f"{n} {t}" for t, n in counts.items()))


if __name__ == "__main__":
    main()
