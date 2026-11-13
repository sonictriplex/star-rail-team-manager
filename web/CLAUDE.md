# CLAUDE.md

Honkai: Star Rail Team- & Build-Manager (Web). Portiert vom Genshin-Team-Manager (`../genshin-team-manager`), gleiche Architektur: eine statische `index.html` (Tailwind per CDN, Vanilla JS, alle Texte deutsch) plus PHP-JSON-API in `api/` mit MySQL/MariaDB. Kein Build-Schritt. PHP prüfen mit `php -l api/<datei>.php`.

- Stammdaten liegen in `../shared/stammdaten.json` (Quelle: Mar-7th/StarRailRes, englische Namen). Aus ihnen erzeugt `python3 tools/make_seed.py` die Datei `seed_stammdaten.sql`; Grafiken liegen in `../shared/assets/images` (`web/assets` ist ein Symlink darauf, beim Deployen auflösen, z. B. `rsync -rL`).
- Datenmodell: `schema.sql`. Ein Build gehört zu Nutzer + Charakter (`user_characters`, UNIQUE `(user_id, char_name)`), nicht zum Team. Relikte stehen als JSON in `relics_json`: je Slot (`Kopf, Hände, Körper, Füße, Planarsphäre, Verbindungsseil`) `{set, rarity, level, main, main_value, substats: [{stat, val} ×4]}`. Slots 1–4 nehmen Sets der Art `Relikt`, Slots 5–6 der Art `Ornament`.
- `api/helpers.php` normalisiert und begrenzt alle Eingaben (`normalize_relics`, `build_values`) und wird von `teams.php` und `transfer.php` gemeinsam genutzt. Grenzen müssen zu den Eingabefeldern passen (Level 1–80, Eidolon 0–6, Talente 1–7 (Basis) bzw. 1–12 (Fertigkeit, Ultimate, Talent), Überlegung S1–S5, Relikt-Level 0–15).
- Austauschformat: `format: star-rail-team-manager`, `version: 1`, `builds`, `teams`.
- Auth, Token-Login und Rate-Limit sind unverändert vom Genshin-Manager übernommen. Leere PHP-Arrays, die das Frontend als Objekt erwartet, als `new stdClass()` ausgeben.
