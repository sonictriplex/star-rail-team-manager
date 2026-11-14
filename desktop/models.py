import html
import json
import os
import re
from typing import Optional

# Projektverzeichnis: alle Pfade werden relativ hierzu aufgelöst,
# damit die App unabhängig vom aktuellen Arbeitsverzeichnis funktioniert.
# Version der Desktop-App; zusammen mit dem Web-Stand hochzählen
APP_VERSION = "1.4.2"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# Gemeinsame Daten für Desktop, Web und weitere Clients (Bilder, Stammdaten)
SHARED_DIR = os.path.join(os.path.dirname(BASE_DIR), "shared")
IMAGES_DIR = os.path.join(SHARED_DIR, "assets", "images")

# Standard-Talentstufen für Charaktere ohne gespeichertes Build (wie in der DB)
DEFAULT_TALENTS = {"basic": 1, "skill": 1, "ult": 1, "talent": 1}
# Höchste Talentstufen (mit Eidolons): Basisangriff 7, Fertigkeit / Ultimate / Talent 12 (wie Web und Server)
TALENT_MAX = {"basic": 7, "skill": 12, "ult": 12, "talent": 12}
MAX_LEVEL = 80

# Die sechs Relikt-Slots. Kopf bis Füße nehmen Relikt-Sets, Planarsphäre und Verbindungsseil Planar-Ornamente.
RELIC_SLOTS = [
    ("Kopf", "Relikt", ["LP (Fix)"]),
    ("Hände", "Relikt", ["ANG (Fix)"]),
    ("Körper", "Relikt", ["LP%", "ANG%", "VTD%", "KT%", "KSCH%", "Heilungsbonus%", "Effekttreffer%"]),
    ("Füße", "Relikt", ["LP%", "ANG%", "VTD%", "Initiative"]),
    ("Planarsphäre", "Ornament", ["LP%", "ANG%", "VTD%", "Physisch-SCH%", "Feuer-SCH%", "Eis-SCH%", "Blitz-SCH%",
                                  "Wind-SCH%", "Quantum-SCH%", "Imaginär-SCH%"]),
    ("Verbindungsseil", "Ornament", ["LP%", "ANG%", "VTD%", "Zerbrechungseffekt%", "Energieregeneration%"]),
]
RELIC_SLOT_NAMES = [name for name, _, _ in RELIC_SLOTS]
SUBSTATS = ["LP (Fix)", "ANG (Fix)", "VTD (Fix)", "LP%", "ANG%", "VTD%", "Initiative", "KT%", "KSCH%",
            "Effekttreffer%", "Effektresistenz%", "Zerbrechungseffekt%"]
RELIC_RARITIES = [5, 4, 3, 2]
MAX_RELIC_LEVEL = 15


def resolve_path(path: str) -> str:
    """Löst einen (in der DB relativ gespeicherten) Pfad gegen das gemeinsame Verzeichnis `shared/` auf."""
    return path if os.path.isabs(path) else os.path.join(SHARED_DIR, path)


ICON_DIRS = {"char": "characters", "lc": "light_cones", "relic": "relic_sets"}


def icon_path_for(prefix: str, name: str) -> str:
    """Leitet den Icon-Pfad aus dem Namen ab, falls in der DB keiner hinterlegt ist (z. B. characters/kafka.png)."""
    slug = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")
    return os.path.join(IMAGES_DIR, ICON_DIRS[prefix], f"{slug}.png")


def _explicit_or_derived(icon_path: Optional[str], prefix: str, name: str) -> str:
    """Der in der DB hinterlegte Pfad, falls die Datei existiert, sonst der aus dem Namen abgeleitete."""
    if icon_path:
        explicit = resolve_path(icon_path)
        if os.path.exists(explicit):
            return explicit
    return icon_path_for(prefix, name)


def tooltip_html(title: str, paragraphs: list[str]) -> str:
    """Tooltip mit fester Breite (Qt bricht Rich-Text-Tooltips sonst nicht um). Texte werden maskiert."""
    body = "".join(f"<p style='margin-top:6px; margin-bottom:0'>{html.escape(p).replace(chr(10), '<br>')}</p>"
                   for p in paragraphs if p)
    return f"<table width='340'><tr><td><b>{html.escape(title)}</b>{body}</td></tr></table>"


class Character:
    """Modelliert einen Star-Rail-Charakter."""
    def __init__(self, name: str, element: str, path: str, rarity: int, icon_path: Optional[str] = None):
        self.name = name
        self.element = element
        self.path = path
        self.rarity = rarity
        self._icon_path = icon_path

    @property
    def icon_path(self) -> str:
        return _explicit_or_derived(self._icon_path, "char", self.name)


class LightCone:
    """Modelliert einen Lichtkegel."""
    def __init__(self, name: str, path: str, rarity: int, icon_path: Optional[str] = None,
                 passive: Optional[dict] = None):
        self.name = name
        self.path = path
        self.rarity = rarity
        self._icon_path = icon_path
        # {"name": Name der Fähigkeit, "text": [Beschreibung je Überlagerung S1..S5]} oder None
        self.passive = passive if isinstance(passive, dict) else None

    def passive_tooltip(self, superimpose: int = 1) -> str:
        """Beschreibung der Fähigkeit bei der gegebenen Überlagerung (1–5), sonst leer."""
        texts = (self.passive or {}).get("text") or []
        if not texts:
            return ""
        step = max(1, min(superimpose, len(texts)))
        return tooltip_html(self.name, [f"{self.passive.get('name', '')} (Überlagerung {step})", texts[step - 1]])

    @property
    def icon_path(self) -> str:
        return _explicit_or_derived(self._icon_path, "lc", self.name)


class RelicSet:
    """Modelliert ein Relikt-Set (kind = "Relikt", 2er und 4er Bonus) oder Planar-Ornament (nur 2er Bonus)."""
    def __init__(self, name: str, kind: str, bonus_2pc: str = "", bonus_4pc: str = "", icon_path: Optional[str] = None,
                 pieces: Optional[dict] = None):
        self.name = name
        self.pieces = pieces or {}   # Namen der einzelnen Teile: {Slot: [Name, ...]}
        self.kind = kind
        self.bonus_2pc = bonus_2pc or ""
        self.bonus_4pc = bonus_4pc or ""
        self._icon_path = icon_path

    @property
    def icon_path(self) -> str:
        return _explicit_or_derived(self._icon_path, "relic", self.name)

    def bonus_tooltip(self) -> str:
        """Set-Boni (2er, 4er) als Tooltip, leer wenn das Set keine Beschreibung hat."""
        parts = [f"{label}-Bonus: {bonus}" for label, bonus in (("2er", self.bonus_2pc), ("4er", self.bonus_4pc)) if bonus]
        pieces = [f"{slot}: {name}" for slot, names in self.pieces.items() for name in names]
        if pieces:
            parts.append("Teile:\n" + "\n".join(pieces))
        return tooltip_html(self.name, parts) if parts else ""


def clamp_int(value, low: int, high: int, default: int) -> int:
    """Begrenzt einen Zahlenwert auf einen Bereich (Fallback bei ungültiger Eingabe), wie clamp_int im Server."""
    try:
        return max(low, min(high, int(float(value))))
    except (TypeError, ValueError):
        return default


_ALIASES = None


def name_aliases() -> dict:
    """Alte englische Namen -> deutsche Namen von Lichtkegeln und Relikt-Sets (shared/name_aliases.json); leer, wenn die Datei fehlt."""
    global _ALIASES
    if _ALIASES is None:
        try:
            with open(os.path.join(SHARED_DIR, "name_aliases.json"), encoding="utf-8") as f:
                _ALIASES = {k: v for k, v in json.load(f).items() if isinstance(v, dict)}
        except (OSError, ValueError):
            _ALIASES = {}
    return _ALIASES


def canonical_name(group: str, name: str) -> str:
    """Deutscher Name zu einem alten englischen (Import alter Export-Dateien); unbekannte Namen bleiben unverändert."""
    return name_aliases().get(group, {}).get(name, name)


def normalize_relics(relics) -> dict:
    """Bringt die Relikte in das Format von Web und Server: nur bekannte Slots, je Slot
    {set, rarity, level, main, main_value, substats: [{stat, val} x4]}."""
    if not isinstance(relics, dict):
        return {}
    clean = {}
    for slot in RELIC_SLOT_NAMES:
        data = relics.get(slot)
        if not isinstance(data, dict):
            continue
        subs_in = data.get("substats") if isinstance(data.get("substats"), list) else []
        subs = []
        for i in range(4):
            sub = subs_in[i] if i < len(subs_in) and isinstance(subs_in[i], dict) else {}
            subs.append({"stat": str(sub.get("stat") or "")[:40], "val": str(sub.get("val") or "")[:20]})
        set_name = str(data.get("set") or "").strip()
        clean[slot] = {
            "set": "" if set_name.startswith("—") else canonical_name("relic_sets", set_name)[:255],
            "rarity": clamp_int(data.get("rarity", 5), 2, 5, 5),
            "level": clamp_int(data.get("level", 15), 0, MAX_RELIC_LEVEL, 15),
            "main": str(data.get("main") or "")[:40],
            "main_value": str(data.get("main_value") or "")[:20],
            "substats": subs,
        }
    return clean


def active_set_bonuses(relics: dict) -> list[tuple[str, int]]:
    """Aktive Set-Boni eines Builds: (Setname, 2 oder 4). Ornamente zählen nur bis 2. Sortiert nach Teilezahl, dann Name."""
    counts: dict[str, int] = {}
    for slot in RELIC_SLOT_NAMES:
        name = (relics.get(slot) or {}).get("set") if isinstance(relics, dict) else ""
        if name:
            counts[name] = counts.get(name, 0) + 1
    active = [(name, 4 if n >= 4 else 2) for name, n in counts.items() if n >= 2]
    return sorted(active, key=lambda x: (-x[1], x[0].casefold()))
