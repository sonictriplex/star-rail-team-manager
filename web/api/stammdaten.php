<?php
// Stammdaten (Charaktere, Lichtkegel, Relikt-Sets) mit der Quelle StarRailRes abgleichen und ergänzen. Nur für Admins.
//   GET  ?action=check                          -> was fehlt (ändert nichts)
//   POST {characters: [Name], light_cones: [Name], relic_sets: [Name]}  -> ausgewählte Einträge eintragen
// Spiegelt tools/update_stammdaten.py; die Datei shared/stammdaten.json im Repository wird dadurch NICHT verändert
// (ein späterer Lauf des Skripts findet die Einträge dort und ergänzt sie lokal).
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth_check.php';
require_once __DIR__ . '/stammdaten_lib.php';

require_login();
require_admin();
set_time_limit(120);

const SRC = 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/';
const MAX_APPLY = 50;
const PATHS = ['Warrior' => 'Zerstörung', 'Rogue' => 'Jagd', 'Mage' => 'Gelehrsamkeit', 'Shaman' => 'Harmonie',
    'Warlock' => 'Nichtigkeit', 'Knight' => 'Bewahrung', 'Priest' => 'Überfluss', 'Memory' => 'Gedenken', 'Elation' => 'Hochgefühl'];
const ELEMENTS = ['Physical' => 'Physisch', 'Fire' => 'Feuer', 'Ice' => 'Eis', 'Thunder' => 'Blitz', 'Wind' => 'Wind',
    'Quantum' => 'Quantum', 'Imaginary' => 'Imaginär'];

function slug(string $name): string {
    return trim(preg_replace('/[^a-z0-9]+/', '_', strtolower($name)), '_');
}

/** Ermittelt, was in der Datenbank fehlt. Rückgabe: [neue Einträge je Gruppe, nicht eintragbare Hinweise]. */
function find_new(PDO $pdo): array {
    $chars = fetch_json(SRC . 'index_new/en/characters.json');
    $lcs = fetch_json(SRC . 'index_new/en/light_cones.json');
    $sets = fetch_json(SRC . 'index_new/en/relic_sets.json');

    $have = [];
    foreach (['characters', 'light_cones', 'relic_sets'] as $t) {
        $have[$t] = array_flip(array_map('mb_strtolower', $pdo->query("SELECT name FROM $t")->fetchAll(PDO::FETCH_COLUMN)));
    }
    $new = ['characters' => [], 'light_cones' => [], 'relic_sets' => []];
    $skipped = [];

    $seen = [];
    foreach ($chars as $id => $c) {
        $path = PATHS[$c['path']] ?? null;
        $element = ELEMENTS[$c['element']] ?? null;
        $name = $c['name'];
        if ($name === '{NICKNAME}') $name = $path ? "Trailblazer ($path)" : $name;   // je Pfad ein Eintrag
        $key = mb_strtolower($name);
        if (isset($have['characters'][$key]) || isset($seen[$key])) continue;
        $seen[$key] = true;
        if (!$path || !$element) { $skipped[] = "Charakter $name: unbekannter Pfad oder Element"; continue; }
        $new['characters'][] = ['name' => $name, 'element' => $element, 'path' => $path, 'rarity' => (int)$c['rarity'],
            'icon_path' => 'assets/images/char_' . slug($name) . '.png', 'src' => "icon/avatar/$id.png"];
    }
    foreach ($lcs as $id => $l) {
        if (isset($have['light_cones'][mb_strtolower($l['name'])])) continue;
        $path = PATHS[$l['path']] ?? null;
        if (!$path) { $skipped[] = "Lichtkegel {$l['name']}: unbekannter Pfad"; continue; }
        $new['light_cones'][] = ['name' => $l['name'], 'path' => $path, 'rarity' => (int)$l['rarity'],
            'icon_path' => 'assets/images/lc_' . slug($l['name']) . '.png', 'src' => $l['icon']];
    }
    foreach ($sets as $id => $s) {
        if (isset($have['relic_sets'][mb_strtolower($s['name'])])) continue;
        $desc = $s['desc'] ?? [];
        $new['relic_sets'][] = ['name' => $s['name'], 'kind' => ((int)$id >= 300) ? 'Ornament' : 'Relikt',
            'bonus_2pc' => $desc[0] ?? null, 'bonus_4pc' => $desc[1] ?? null,
            'icon_path' => 'assets/images/relic_' . slug($s['name']) . '.png', 'src' => $s['icon']];
    }
    return [$new, $skipped];
}

function public_view(array $new): array {
    $out = [];
    foreach ($new as $group => $items) {
        $out[$group] = array_map(fn($e) => array_diff_key($e, ['src' => 1, 'icon_path' => 1, 'bonus_2pc' => 1, 'bonus_4pc' => 1]), $items);
    }
    return $out;
}

try {
    if (($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        [$new, $skipped] = find_new($pdo);
        echo json_encode(["new" => public_view($new), "skipped" => $skipped], JSON_UNESCAPED_UNICODE);
        exit();
    }

    $in = require_ajax_post();
    $selected = [];
    $total = 0;
    foreach (['characters', 'light_cones', 'relic_sets'] as $g) {
        $selected[$g] = array_flip(array_map('mb_strtolower', array_filter((array)($in[$g] ?? []), 'is_string')));
        $total += count($selected[$g]);
    }
    if ($total === 0) { echo json_encode(["added" => 0, "images" => new stdClass()], JSON_UNESCAPED_UNICODE); exit(); }
    if ($total > MAX_APPLY) {
        http_response_code(400);
        echo json_encode(["error" => "Zu viele Einträge auf einmal (höchstens " . MAX_APPLY . ")."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    [$new] = find_new($pdo);   // neu aus der Quelle berechnen: dem Browser wird nur die Auswahl der Namen geglaubt
    $stmts = [
        'characters' => $pdo->prepare("INSERT IGNORE INTO characters (name, element, path, rarity, icon_path) VALUES (?, ?, ?, ?, ?)"),
        'light_cones' => $pdo->prepare("INSERT IGNORE INTO light_cones (name, path, rarity, icon_path) VALUES (?, ?, ?, ?)"),
        'relic_sets' => $pdo->prepare("INSERT IGNORE INTO relic_sets (name, kind, bonus_2pc, bonus_4pc, icon_path) VALUES (?, ?, ?, ?, ?)"),
    ];
    $added = 0;
    $images = ['geladen' => 0, 'vorhanden' => 0, 'fehler' => 0];
    $pdo->beginTransaction();
    foreach ($new as $group => $items) {
        foreach ($items as $e) {
            if (!isset($selected[$group][mb_strtolower($e['name'])])) continue;
            $args = match ($group) {
                'characters' => [$e['name'], $e['element'], $e['path'], $e['rarity'], $e['icon_path']],
                'light_cones' => [$e['name'], $e['path'], $e['rarity'], $e['icon_path']],
                'relic_sets' => [$e['name'], $e['kind'], $e['bonus_2pc'], $e['bonus_4pc'], $e['icon_path']],
            };
            $stmts[$group]->execute($args);
            $added += $stmts[$group]->rowCount();
            $state = save_image(SRC . $e['src'], basename($e['icon_path']));
            $images[str_starts_with($state, 'FEHLER') ? 'fehler' : $state]++;
        }
    }
    $pdo->commit();
    echo json_encode(["added" => $added, "images" => $images], JSON_UNESCAPED_UNICODE);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log("stammdaten.php: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(["error" => "Die Datenquelle konnte nicht abgefragt oder die Daten nicht gespeichert werden."], JSON_UNESCAPED_UNICODE);
}
