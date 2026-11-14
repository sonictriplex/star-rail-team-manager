# Austauschformat (Builds und Teams)

Gemeinsames JSON-Format aller Clients: Desktop (Menü „Datei“), Web (`web/api/transfer.php`, Benutzermenü „Exportieren“ und „Importieren“) und Android (über die API). Eine Export-Datei lässt sich in jedem Client einlesen.

**Änderungen am Format müssen in allen Clients nachgezogen werden.** Abwärtskompatible Erweiterungen (neue optionale Felder) behalten die `version`. Inkompatible Änderungen erhöhen sie; ältere Clients lehnen dann die Datei ab.

## Aufbau

```json
{
  "format": "star-rail-team-manager",
  "version": 1,
  "exported_at": "2026-11-14T12:00:00",
  "builds": [ ... ],
  "teams":  [ ... ]
}
```

| Feld | Bedeutung |
|---|---|
| `format` | Muss `star-rail-team-manager` sein (Dateien anderer Programme, auch des Genshin-Managers, werden abgelehnt) |
| `version` | Ganzzahl, aktuell `1`. Ist sie größer als die vom Client unterstützte, wird der Import abgelehnt |
| `exported_at` | Zeitstempel (ISO 8601, ohne Zeitzone) |
| `builds` | Builds je Charakter (ein Build pro Charakter, nicht pro Team) |
| `teams` | Teams mit Charakternamen in Slot-Reihenfolge |

### Build

| Feld | Typ / Bereich | Hinweis |
|---|---|---|
| `name` | String | Charaktername, muss in den Stammdaten existieren |
| `level` | 1–80 | Standard 80 |
| `eidolon` | 0–6 | Standard 0 |
| `light_cone` | String | Name des Lichtkegels, leer = keiner; Platzhalter (beginnt mit `—`) = keiner |
| `lc_level` | 1–80 | Level des Lichtkegels, Standard 80 |
| `lc_superimpose` | 1–5 | Überlagerung (S1–S5), Standard 1 |
| `talents` | `{basic, skill, ult, talent}` | `basic` 1–7, die übrigen 1–12, Standard 1 |
| `relics` | Objekt | Slotname → Slot; `{}` wenn leer |

**Relikt-Slot** (Schlüssel: `Kopf`, `Hände`, `Körper`, `Füße`, `Planarsphäre`, `Verbindungsseil`; andere Schlüssel werden verworfen):

| Feld | Typ / Bereich | Hinweis |
|---|---|---|
| `set` | String | Name des Relikt-Sets (Kopf bis Füße) bzw. Planar-Ornaments (Planarsphäre, Verbindungsseil); leer = keins |
| `rarity` | 2–5 | Zahl, Standard 5 |
| `level` | 0–15 | Standard 15 |
| `main` | String | Hauptwert, z. B. `KT%`; muss zum Slot passen (Kopf `LP (Fix)`, Hände `ANG (Fix)`, …) |
| `main_value` | String | Wert als Text, z. B. `"64.8"` |
| `substats` | Liste | immer genau 4 Einträge `{stat, val}` (leere als `""`) |

Werte (`main_value`, `val`) sind Strings; die Stats heißen `LP (Fix)`, `ANG (Fix)`, `VTD (Fix)`, `LP%`, `ANG%`, `VTD%`, `Initiative`, `KT%`, `KSCH%`, `Effekttreffer%`, `Effektresistenz%`, `Zerbrechungseffekt%`; Hauptwerte zusätzlich `Heilungsbonus%`, `Energieregeneration%` und `<Element>-SCH%` (`Physisch-SCH%`, `Feuer-SCH%`, `Eis-SCH%`, `Blitz-SCH%`, `Wind-SCH%`, `Quantum-SCH%`, `Imaginär-SCH%`).

Beispiel eines Builds:

```json
{
  "name": "Kafka",
  "level": 80,
  "eidolon": 1,
  "light_cone": "Along the Passing Shore",
  "lc_level": 80,
  "lc_superimpose": 1,
  "talents": { "basic": 6, "skill": 10, "ult": 10, "talent": 10 },
  "relics": {
    "Kopf": {
      "set": "Band of Sizzling Thunder", "rarity": 5, "level": 15,
      "main": "LP (Fix)", "main_value": "705.6",
      "substats": [
        { "stat": "KT%", "val": "5.2" }, { "stat": "KSCH%", "val": "10.4" },
        { "stat": "", "val": "" }, { "stat": "", "val": "" }
      ]
    }
  }
}
```

### Team

`{ "name": "…", "members": ["Charaktername", …] }`: höchstens 4 Mitglieder, ohne Duplikate, Reihenfolge = Slot.

## Import-Regeln (für alle Clients gleich)

- Unbekannte Charaktere werden übersprungen und in `skipped` gemeldet.
- Zahlen werden auf die Bereiche oben begrenzt; ungültige Werte fallen auf den Standard zurück.
- Gleichnamige Builds und Teams werden überschrieben (`merge`); bei `replace` werden vorher alle Builds und Teams des Nutzers gelöscht.
- Der Import läuft in einer Transaktion: bei einem Fehler bleibt alles unverändert.
- Ergebnis: `{builds: n, teams: n, skipped: [Namen]}`.

## Namen

Charakternamen, Lichtkegel und Sets heißen **englisch** wie in `shared/stammdaten.json`. Die Trailblazer stehen je Pfad einmal als `Trailblazer (Zerstörung)`, `Trailblazer (Bewahrung)`, `Trailblazer (Harmonie)`, `Trailblazer (Gedenken)` und `Trailblazer (Hochgefühl)` (ohne Artikel). Gleichnamige Charaktere der Quelle tragen den Pfad als Zusatz, z. B. `March 7th (Die Jagd)`.
