<?php
// Gemeinsame Hilfsfunktionen für teams.php und transfer.php

/** Die sechs Relikt-Slots (Schlüssel in relics_json). */
const RELIC_SLOTS = ['Kopf', 'Hände', 'Körper', 'Füße', 'Planarsphäre', 'Verbindungsseil'];

/** Begrenzt einen Zahlenwert auf einen Bereich (Fallback bei ungültiger Eingabe). */
function clamp_int($value, int $min, int $max, int $default): int {
    if (!is_numeric($value)) return $default;
    return max($min, min($max, (int)$value));
}

/** Platzhaltertexte wie "— Kein Set —" werden zu einem leeren Wert. */
function clean_set($value): string {
    $v = trim((string)$value);
    if ($v === '' || mb_strpos($v, '—') === 0) return '';
    return $v;
}

/**
 * Alte englische Namen von Lichtkegeln und Relikt-Sets (Export-Dateien, ältere App-Versionen) auf den deutschen Namen
 * abbilden. Die Tabelle steht in name_aliases.php (erzeugt von web/tools/make_seed.py).
 */
function canonical_name(string $group, string $name): string {
    static $aliases = null;
    $aliases ??= require __DIR__ . '/name_aliases.php';
    return $aliases[$group][$name] ?? $name;
}

/**
 * Bringt relics in ein sauberes, immer gleiches Format:
 * - Rückgabe ist ein assoziatives Array (Slot => Daten) oder leer; nur bekannte Slots
 * - je Slot {set, rarity, level, main, main_value, substats: [{stat, val} x4]}
 */
function normalize_relics($relics): array {
    if (!is_array($relics)) return [];

    $clean = [];
    foreach (RELIC_SLOTS as $slotName) {
        $slot = $relics[$slotName] ?? null;
        if (!is_array($slot)) continue;

        $subsIn = (isset($slot['substats']) && is_array($slot['substats'])) ? array_values($slot['substats']) : [];
        $subs = [];
        for ($i = 0; $i < 4; $i++) {
            $e = $subsIn[$i] ?? [];
            if (!is_array($e)) $e = [];
            $subs[] = [
                'stat' => mb_substr((string)($e['stat'] ?? ''), 0, 40),
                'val'  => mb_substr((string)($e['val'] ?? ''), 0, 20),
            ];
        }

        $clean[$slotName] = [
            'set'        => mb_substr(canonical_name('relic_sets', clean_set($slot['set'] ?? '')), 0, 255),
            'rarity'     => clamp_int($slot['rarity'] ?? 5, 2, 5, 5),
            'level'      => clamp_int($slot['level'] ?? 15, 0, 15, 15),
            'main'       => mb_substr((string)($slot['main'] ?? ''), 0, 40),
            'main_value' => mb_substr((string)($slot['main_value'] ?? ''), 0, 20),
            'substats'   => $subs,
        ];
    }
    return $clean;
}

/** Liest relics_json aus der Datenbank und liefert es immer als Array (leer = []). */
function decode_relics($json): array {
    $data = !empty($json) ? json_decode($json, true) : null;
    return normalize_relics($data);
}

/** Baut aus einer Eingabe (Team-Mitglied oder Build) die geprüften Spaltenwerte für user_characters. */
function build_values(array $m): array {
    $talents = is_array($m['talents'] ?? null) ? $m['talents'] : [];
    $relics = normalize_relics($m['relics'] ?? []);
    return [
        clamp_int($m['level'] ?? 80, 1, 80, 80),
        clamp_int($m['eidolon'] ?? 0, 0, 6, 0),
        clamp_int($talents['basic'] ?? 1, 1, 7, 1),
        clamp_int($talents['skill'] ?? 1, 1, 12, 1),
        clamp_int($talents['ult'] ?? 1, 1, 12, 1),
        clamp_int($talents['talent'] ?? 1, 1, 12, 1),
        canonical_name('light_cones', clean_set($m['light_cone'] ?? '')),
        clamp_int($m['lc_level'] ?? 80, 1, 80, 80),
        clamp_int($m['lc_superimpose'] ?? 1, 1, 5, 1),
        json_encode(count($relics) > 0 ? $relics : new stdClass(), JSON_UNESCAPED_UNICODE),
    ];
}

const BUILD_UPSERT_SQL = "
    INSERT INTO user_characters (
        user_id, char_name, level, eidolon, talent_basic, talent_skill, talent_ult, talent_talent,
        light_cone, lc_level, lc_superimpose, relics_json
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE
        level=VALUES(level), eidolon=VALUES(eidolon), talent_basic=VALUES(talent_basic),
        talent_skill=VALUES(talent_skill), talent_ult=VALUES(talent_ult), talent_talent=VALUES(talent_talent),
        light_cone=VALUES(light_cone), lc_level=VALUES(lc_level), lc_superimpose=VALUES(lc_superimpose),
        relics_json=VALUES(relics_json)
";

/** Wandelt eine Zeile aus user_characters (ggf. mit NULLs) in das Build-Format der API um. */
function build_from_row(array $r, string $name): array {
    $relics = decode_relics($r['relics_json'] ?? null);
    return [
        "name"           => $name,
        "level"          => (int)($r['level'] ?? 80),
        "eidolon"        => (int)($r['eidolon'] ?? 0),
        "light_cone"     => $r['light_cone'] ?? '',
        "lc_level"       => (int)($r['lc_level'] ?? 80),
        "lc_superimpose" => (int)($r['lc_superimpose'] ?? 1),
        "relics"         => count($relics) > 0 ? $relics : new stdClass(),
        "talents"        => [
            "basic"  => (int)($r['talent_basic'] ?? 1),
            "skill"  => (int)($r['talent_skill'] ?? 1),
            "ult"    => (int)($r['talent_ult'] ?? 1),
            "talent" => (int)($r['talent_talent'] ?? 1),
        ],
    ];
}
