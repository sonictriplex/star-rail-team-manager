import json
import os
import sqlite3
from datetime import datetime
from typing import Optional
from models import (Character, LightCone, RelicSet, BASE_DIR, DEFAULT_TALENTS, MAX_LEVEL, TALENT_MAX,
                    canonical_name, clamp_int, icon_path_for, name_aliases, normalize_relics)
from seed import SEED_PATH, fill_texts, import_seed, is_seeded

DB_PATH = os.path.join(BASE_DIR, "star_rail_manager.db")

# Austauschformat für Import/Export zwischen Desktop- und Web-Version
EXCHANGE_FORMAT = "star-rail-team-manager"
EXCHANGE_VERSION = 1
MAX_TEAM_SIZE = 4

SCHEMA = """
CREATE TABLE IF NOT EXISTS characters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    element TEXT NOT NULL,
    path TEXT NOT NULL,
    rarity INTEGER NOT NULL,
    icon_path TEXT
);
CREATE TABLE IF NOT EXISTS light_cones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    path TEXT NOT NULL,
    rarity INTEGER NOT NULL,
    icon_path TEXT,
    passive_json TEXT
);
CREATE TABLE IF NOT EXISTS relic_sets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    kind TEXT NOT NULL,
    bonus_2pc TEXT DEFAULT '',
    bonus_4pc TEXT DEFAULT '',
    icon_path TEXT
);
CREATE TABLE IF NOT EXISTS user_characters (
    char_name TEXT PRIMARY KEY,
    level INTEGER DEFAULT 80,
    eidolon INTEGER DEFAULT 0,
    talent_basic INTEGER DEFAULT 1,
    talent_skill INTEGER DEFAULT 1,
    talent_ult INTEGER DEFAULT 1,
    talent_talent INTEGER DEFAULT 1,
    light_cone TEXT DEFAULT '',
    lc_level INTEGER DEFAULT 80,
    lc_superimpose INTEGER DEFAULT 1,
    relics_json TEXT DEFAULT '{}',
    FOREIGN KEY (char_name) REFERENCES characters(name) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE TABLE IF NOT EXISTS teams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    position INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS team_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    team_id INTEGER NOT NULL,
    char_name TEXT NOT NULL,
    slot_order INTEGER NOT NULL,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    FOREIGN KEY (char_name) REFERENCES characters(name) ON DELETE CASCADE,
    UNIQUE (team_id, slot_order)
);
"""


class DatabaseError(Exception):
    """Fehler beim Lesen oder Schreiben der Datenbank (mit verständlicher Meldung für die GUI)."""


class StarRailManager:
    """Verwaltet Charaktere, Lichtkegel, Relikt-Sets, gespeicherte Teams und globale Charakter-Builds via SQLite."""

    def __init__(self, db_path: str = DB_PATH, seed_path: Optional[str] = SEED_PATH):
        self.db_path = db_path
        self.seed_path = seed_path  # None = Stammdaten nicht automatisch einspielen
        self.available_characters: list[Character] = []
        self.available_light_cones: list[LightCone] = []
        self.available_relic_sets: list[RelicSet] = []
        self.saved_teams: dict = {}
        # Fehler beim Start werden gesammelt, damit die GUI sie anzeigen kann
        self.load_errors: list[str] = []

        for step in (self.init_schema, self.seed_if_empty, self.load_characters, self.load_light_cones,
                     self.load_relic_sets, self.load_teams):
            try:
                step()
            except DatabaseError as e:
                self.load_errors.append(str(e))

    def get_connection(self) -> sqlite3.Connection:
        """Erstellt eine SQLite-Verbindung mit aktivierten Foreign Keys."""
        conn = sqlite3.connect(self.db_path)
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def init_schema(self):
        """Legt fehlende Tabellen an, damit auch eine neue/leere Datenbank funktioniert."""
        try:
            with self.get_connection() as conn:
                conn.executescript(SCHEMA)
                self._migrate_german_names(conn)   # vor den Fähigkeiten: die Texte werden über die deutschen Namen zugeordnet
                self._migrate_light_cone_passives(conn)
        except (sqlite3.Error, OSError, json.JSONDecodeError) as e:
            raise DatabaseError(f"Datenbank konnte nicht initialisiert werden ({self.db_path}): {e}") from e

    def _migrate_light_cone_passives(self, conn: sqlite3.Connection):
        """Ältere Datenbanken: Spalte light_cones.passive_json (Fähigkeiten) ergänzen und mit den deutschen Texten
        (auch der Set-Boni) aus der Seed-Datei füllen."""
        columns = [r[1] for r in conn.execute("PRAGMA table_info(light_cones)")]
        if "passive_json" in columns:
            return
        conn.execute("ALTER TABLE light_cones ADD COLUMN passive_json TEXT")
        if self.seed_path and os.path.exists(self.seed_path):
            fill_texts(conn, self.seed_path)

    @staticmethod
    def _migrate_german_names(conn: sqlite3.Connection):
        """Ältere Datenbanken: Lichtkegel und Relikt-Sets tragen noch englische Namen. Umbenennen auf die deutschen, samt der
        gespeicherten Builds (Lichtkegel und Set-Namen in relics_json). Läuft bei jedem Start, ist bei bereits deutschen Namen wirkungslos."""
        aliases = name_aliases()
        lcs, sets = aliases.get("light_cones", {}), aliases.get("relic_sets", {})
        for old, new in lcs.items():
            conn.execute("UPDATE light_cones SET name = ? WHERE name = ? AND NOT EXISTS (SELECT 1 FROM light_cones WHERE name = ?)", (new, old, new))
            conn.execute("UPDATE user_characters SET light_cone = ? WHERE light_cone = ?", (new, old))
        for old, new in sets.items():
            conn.execute("UPDATE relic_sets SET name = ? WHERE name = ? AND NOT EXISTS (SELECT 1 FROM relic_sets WHERE name = ?)", (new, old, new))
        if not sets:
            return
        for char_name, raw in conn.execute("SELECT char_name, relics_json FROM user_characters WHERE relics_json LIKE '%\"set\"%'").fetchall():
            try:
                relics = json.loads(raw or "{}")
            except (json.JSONDecodeError, TypeError):
                continue
            changed = False
            for slot in relics.values() if isinstance(relics, dict) else []:
                if isinstance(slot, dict) and slot.get("set") in sets:
                    slot["set"] = sets[slot["set"]]
                    changed = True
            if changed:
                conn.execute("UPDATE user_characters SET relics_json = ? WHERE char_name = ?", (json.dumps(relics, ensure_ascii=False), char_name))

    def seed_if_empty(self):
        """Spielt die Stammdaten aus der JSON-Datei ein, wenn die Datenbank noch keine hat."""
        if not self.seed_path:
            return
        try:
            with self.get_connection() as conn:
                if is_seeded(conn):
                    return
                if not os.path.exists(self.seed_path):
                    raise DatabaseError(
                        f"Die Datenbank enthält keine Stammdaten und die Datei {self.seed_path} fehlt."
                    )
                import_seed(conn, self.seed_path)
        except (sqlite3.Error, OSError, json.JSONDecodeError) as e:
            raise DatabaseError(f"Stammdaten konnten nicht eingespielt werden ({self.seed_path}): {e}") from e

    # --- STAMMDATEN LADEN ---

    def load_characters(self):
        """Lädt alle Charaktere aus der SQLite-Datenbank."""
        try:
            with self.get_connection() as conn:
                rows = conn.execute("SELECT name, element, path, rarity, icon_path FROM characters ORDER BY name ASC").fetchall()
                self.available_characters = [Character(name=r[0], element=r[1], path=r[2], rarity=r[3], icon_path=r[4])
                                             for r in rows]
        except sqlite3.Error as e:
            self.available_characters = []
            raise DatabaseError(f"Fehler beim Laden der Charaktere: {e}") from e

    def load_light_cones(self):
        """Lädt alle Lichtkegel aus der SQLite-Datenbank."""
        try:
            with self.get_connection() as conn:
                rows = conn.execute("SELECT name, path, rarity, icon_path, passive_json FROM light_cones ORDER BY name ASC").fetchall()
                self.available_light_cones = [LightCone(name=r[0], path=r[1], rarity=r[2], icon_path=r[3],
                                                        passive=self._parse_json(r[4])) for r in rows]
        except sqlite3.Error as e:
            self.available_light_cones = []
            raise DatabaseError(f"Fehler beim Laden der Lichtkegel: {e}") from e

    def load_relic_sets(self):
        """Lädt Relikt-Sets und Planar-Ornamente aus der SQLite-Datenbank."""
        try:
            with self.get_connection() as conn:
                rows = conn.execute("SELECT name, kind, bonus_2pc, bonus_4pc, icon_path FROM relic_sets ORDER BY name ASC").fetchall()
                self.available_relic_sets = [RelicSet(name=r[0], kind=r[1], bonus_2pc=r[2] or "", bonus_4pc=r[3] or "",
                                                      icon_path=r[4]) for r in rows]
        except sqlite3.Error as e:
            self.available_relic_sets = []
            raise DatabaseError(f"Fehler beim Laden der Relikt-Sets: {e}") from e

    @staticmethod
    def _parse_json(text: Optional[str]):
        try:
            return json.loads(text) if text else None
        except json.JSONDecodeError:
            return None

    def relic_sets_of_kind(self, kind: str) -> list[RelicSet]:
        """Relikt-Sets (kind "Relikt") oder Planar-Ornamente (kind "Ornament")."""
        return [r for r in self.available_relic_sets if r.kind == kind]

    # --- TEAMS VERWALTEN ---

    def load_teams(self):
        """Lädt alle gespeicherten Teams und deren Mitgliederzuordnung aus der Datenbank."""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT id, name FROM teams ORDER BY position ASC, id ASC")
                teams_rows = cursor.fetchall()

                teams_dict = {}
                for team_id, team_name in teams_rows:
                    cursor.execute("""
                        SELECT char_name FROM team_members 
                        WHERE team_id = ? ORDER BY slot_order ASC
                    """, (team_id,))
                    members = [m[0] for m in cursor.fetchall()]
                    teams_dict[team_name] = members

                self.saved_teams = teams_dict
        except sqlite3.Error as e:
            self.saved_teams = {}
            raise DatabaseError(f"Fehler beim Laden der Teams: {e}") from e

    def save_teams(self):
        """Speichert alle Teams aus self.saved_teams in der SQLite-Datenbank."""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()
                
                # 1. Bestehende Teams ermitteln und gelöschte entfernen
                cursor.execute("SELECT name FROM teams")
                existing_teams = {row[0] for row in cursor.fetchall()}
                current_teams = set(self.saved_teams.keys())

                teams_to_delete = existing_teams - current_teams
                for t_name in teams_to_delete:
                    cursor.execute("DELETE FROM teams WHERE name = ?", (t_name,))

                # 2. Teams und Mitglieder aktualisieren / einfügen
                for team_name, char_names in self.saved_teams.items():
                    cursor.execute(
                        "INSERT OR IGNORE INTO teams (name, position) "
                        "SELECT ?, COALESCE(MAX(position), 0) + 1 FROM teams", (team_name,))
                    cursor.execute("SELECT id FROM teams WHERE name = ?", (team_name,))
                    team_id = cursor.fetchone()[0]

                    # Alte Zuordnungen überschreiben
                    cursor.execute("DELETE FROM team_members WHERE team_id = ?", (team_id,))

                    for slot_idx, c_entry in enumerate(char_names):
                        name = c_entry.get("name", c_entry) if isinstance(c_entry, dict) else c_entry
                        if hasattr(name, "name"):
                            name = name.name

                        if name:
                            cursor.execute("""
                                INSERT INTO team_members (team_id, char_name, slot_order)
                                VALUES (?, ?, ?)
                            """, (team_id, name, slot_idx))

                conn.commit()
        except sqlite3.Error as e:
            # Die Transaktion wurde zurückgerollt -> Speicherstand wieder mit der DB abgleichen
            try:
                self.load_teams()
            except DatabaseError:
                pass
            raise DatabaseError(f"Teams konnten nicht gespeichert werden: {e}") from e

    def rename_team(self, old_name: str, new_name: str):
        """Benennt ein Team in der DB um (ID und Reihenfolge bleiben erhalten).

        Ein bereits existierendes Team mit dem neuen Namen wird dabei ersetzt.
        """
        try:
            with self.get_connection() as conn:
                conn.execute("DELETE FROM teams WHERE name = ?", (new_name,))
                conn.execute("UPDATE teams SET name = ? WHERE name = ?", (new_name, old_name))
        except sqlite3.Error as e:
            raise DatabaseError(f"Team '{old_name}' konnte nicht umbenannt werden: {e}") from e

        # Speicherstand anpassen, Position des Teams beibehalten
        self.saved_teams = {
            (new_name if name == old_name else name): members
            for name, members in self.saved_teams.items()
            if name != new_name
        }

    def set_team_order(self, names: list):
        """Speichert die Reihenfolge der Teams (Namen in der gewünschten Reihenfolge) und passt den Speicherstand an."""
        try:
            with self.get_connection() as conn:
                for position, name in enumerate(names, start=1):
                    conn.execute("UPDATE teams SET position = ? WHERE name = ?", (position, name))
        except sqlite3.Error as e:
            raise DatabaseError(f"Reihenfolge der Teams konnte nicht gespeichert werden: {e}") from e

        ordered = {n: self.saved_teams[n] for n in names if n in self.saved_teams}
        ordered.update({n: m for n, m in self.saved_teams.items() if n not in ordered})
        self.saved_teams = ordered

    # --- ZENTRALE CHARAKTER-BUILDS VERWALTEN ---

    @staticmethod
    def build_values(char_name: str, card_data: dict) -> tuple:
        """Geprüfte Spaltenwerte eines Builds (Grenzen wie im Server, siehe helpers.php build_values)."""
        talents = card_data.get("talents") or {}
        relics = normalize_relics(card_data.get("relics") or {})
        return (
            char_name,
            clamp_int(card_data.get("level", MAX_LEVEL), 1, MAX_LEVEL, MAX_LEVEL),
            clamp_int(card_data.get("eidolon", 0), 0, 6, 0),
            *(clamp_int(talents.get(key, DEFAULT_TALENTS[key]), 1, TALENT_MAX[key], 1)
              for key in ("basic", "skill", "ult", "talent")),
            canonical_name("light_cones", str(card_data.get("light_cone") or "")),
            clamp_int(card_data.get("lc_level", MAX_LEVEL), 1, MAX_LEVEL, MAX_LEVEL),
            clamp_int(card_data.get("lc_superimpose", 1), 1, 5, 1),
            json.dumps(relics, ensure_ascii=False),
        )

    BUILD_SQL = """
        INSERT OR REPLACE INTO user_characters (
            char_name, level, eidolon, talent_basic, talent_skill, talent_ult, talent_talent,
            light_cone, lc_level, lc_superimpose, relics_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    def load_user_characters(self) -> dict:
        """Lädt alle zentral gespeicherten Charakter-Builds aus der Datenbank (Format wie in der Export-Datei)."""
        try:
            with self.get_connection() as conn:
                rows = conn.execute("""
                    SELECT char_name, level, eidolon, talent_basic, talent_skill, talent_ult, talent_talent,
                           light_cone, lc_level, lc_superimpose, relics_json
                    FROM user_characters
                """).fetchall()
        except sqlite3.Error as e:
            raise DatabaseError(f"Fehler beim Laden der Charakter-Builds: {e}") from e

        user_builds = {}
        for r in rows:
            try:
                relics = normalize_relics(json.loads(r[10] or "{}"))
            except (json.JSONDecodeError, TypeError):
                relics = {}
            user_builds[r[0]] = {
                "name": r[0], "level": r[1], "eidolon": r[2],
                "light_cone": r[7] or "", "lc_level": r[8], "lc_superimpose": r[9],
                "relics": relics,
                "talents": {"basic": r[3], "skill": r[4], "ult": r[5], "talent": r[6]},
            }
        return user_builds

    def save_user_character(self, char_name: str, card_data: dict):
        """Speichert oder aktualisiert das globale Build-Profil eines Charakters in SQLite."""
        try:
            with self.get_connection() as conn:
                conn.execute(self.BUILD_SQL, self.build_values(char_name, card_data))
                conn.commit()
        except sqlite3.Error as e:
            raise DatabaseError(f"Build für '{char_name}' konnte nicht gespeichert werden: {e}") from e

    # --- IMPORT / EXPORT ---

    def export_user_data(self, path: str):
        """Schreibt Charakter-Builds und Teams (ohne Stammdaten) als JSON-Datei."""
        builds = self.load_user_characters()
        self.load_teams()
        teams = [
            {"name": name, "members": [m.get("name", "") if isinstance(m, dict) else m for m in members]}
            for name, members in self.saved_teams.items()
        ]
        data = {
            "format": EXCHANGE_FORMAT,
            "version": EXCHANGE_VERSION,
            "exported_at": datetime.now().isoformat(timespec="seconds"),
            "builds": list(builds.values()),
            "teams": teams,
        }
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except OSError as e:
            raise DatabaseError(f"Export nach {path} fehlgeschlagen: {e}") from e

    def import_user_data(self, path: str, replace: bool = False) -> dict:
        """Liest eine Export-Datei ein. Gleichnamige Builds/Teams werden überschrieben.

        replace=True löscht vorher alle vorhandenen Builds und Teams. Unbekannte Charaktere werden
        übersprungen. Rückgabe: {"builds": n, "teams": n, "skipped": [Namen]}.
        """
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, json.JSONDecodeError) as e:
            raise DatabaseError(f"Datei konnte nicht gelesen werden: {e}") from e

        if not isinstance(data, dict) or data.get("format") != EXCHANGE_FORMAT:
            raise DatabaseError("Die Datei ist keine Export-Datei des Star-Rail-Team-Managers.")
        if not isinstance(data.get("version"), int) or data["version"] > EXCHANGE_VERSION:
            raise DatabaseError("Die Datei stammt aus einer neueren Version und kann nicht gelesen werden.")

        result = {"builds": 0, "teams": 0, "skipped": []}
        try:
            with self.get_connection() as conn:
                known = {r[0] for r in conn.execute("SELECT name FROM characters")}
                if replace:
                    conn.execute("DELETE FROM teams")
                    conn.execute("DELETE FROM user_characters")

                for b in data.get("builds", []):
                    name = b.get("name") if isinstance(b, dict) else None
                    if name not in known:
                        result["skipped"].append(str(name))
                        continue
                    conn.execute(self.BUILD_SQL, self.build_values(name, b))
                    result["builds"] += 1

                for t in data.get("teams", []):
                    team_name = (t.get("name") or "").strip() if isinstance(t, dict) else ""
                    if not team_name:
                        continue
                    members = []
                    for m in t.get("members", []):
                        if m in known and m not in members:
                            members.append(m)
                        elif m not in known:
                            result["skipped"].append(str(m))
                    conn.execute(
                        "INSERT OR IGNORE INTO teams (name, position) "
                        "SELECT ?, COALESCE(MAX(position), 0) + 1 FROM teams", (team_name,))
                    team_id = conn.execute("SELECT id FROM teams WHERE name = ?", (team_name,)).fetchone()[0]
                    conn.execute("DELETE FROM team_members WHERE team_id = ?", (team_id,))
                    for slot, m in enumerate(members[:MAX_TEAM_SIZE]):
                        conn.execute(
                            "INSERT INTO team_members (team_id, char_name, slot_order) VALUES (?, ?, ?)",
                            (team_id, m, slot),
                        )
                    result["teams"] += 1
        except (sqlite3.Error, AttributeError, TypeError) as e:
            raise DatabaseError(f"Import fehlgeschlagen, es wurde nichts verändert: {e}") from e

        self.load_teams()
        result["skipped"] = sorted(set(result["skipped"]))
        return result

    # --- HILFSMETHODEN ---

    def get_character_by_name(self, name: str) -> Character:
        """Sucht einen Charakter nach Namen im Speicher oder erzeugt ein Fallback-Objekt."""
        for c in self.available_characters:
            if c.name.lower() == name.lower():
                return c
        return Character(name=name, element="Physisch", path="", rarity=4, icon_path=icon_path_for("char", name))

    def get_light_cone_by_name(self, name: str) -> Optional[LightCone]:
        """Sucht einen Lichtkegel nach Namen."""
        for lc in self.available_light_cones:
            if lc.name.lower() == (name or "").lower():
                return lc
        return None

    def get_light_cones_by_path(self, path: str) -> list[LightCone]:
        """Gibt alle Lichtkegel eines Pfads zurück."""
        return [lc for lc in self.available_light_cones if lc.path == path]

    def get_relic_set_by_name(self, name: str) -> Optional[RelicSet]:
        """Sucht ein Relikt-Set oder Planar-Ornament nach Namen."""
        for r in self.available_relic_sets:
            if r.name.lower() == (name or "").lower():
                return r
        return None
