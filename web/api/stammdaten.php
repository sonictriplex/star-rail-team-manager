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
const PATHS = ['Warrior' => 'Die Zerstörung', 'Rogue' => 'Die Jagd', 'Mage' => 'Die Gelehrsamkeit', 'Shaman' => 'Die Harmonie',
    'Warlock' => 'Die Nichtigkeit', 'Knight' => 'Die Bewahrung', 'Priest' => 'Der Überfluss', 'Memory' => 'Das Gedenken', 'Elation' => 'Das Hochgefühl'];
const ELEMENTS = ['Physical' => 'Physisch', 'Fire' => 'Feuer', 'Ice' => 'Eis', 'Thunder' => 'Blitz', 'Wind' => 'Wind',
    'Quantum' => 'Quantum', 'Imaginary' => 'Imaginär'];

/** Deutsche Texte der Quelle (Fähigkeiten der Lichtkegel, Boni der Sets), je Anfrage einmal geladen. */
function de_source(string $file): array {
    static $cache = [];
    return $cache[$file] ??= fetch_json(SRC . 'index_new/de/' . $file);
}

/**
 * Setzt die Werte einer Überlagerung in den Text der Lichtkegel-Fähigkeit ein (wie tools/update_stammdaten.py).
 * #1[i] ganze Zahl, #1[f1] eine Nachkommastelle, ein folgendes % bedeutet Prozent (Wert x 100);
 * Geschlechtsvarianten {F#…}{M#…} werden zu "weiblich/männlich".
 */
function render_rank(string $desc, array $params): string {
    $text = preg_replace_callback('/#(\d+)\[([^\]]+)\](%?)/', function ($m) use ($params) {
        $value = $params[(int)$m[1] - 1] * ($m[3] === '%' ? 100 : 1);
        if ($m[2] === 'i') {
            $t = (string)(int)floor($value + 0.5 + 1e-9);
        } else {
            $t = number_format($value, (int)substr($m[2], 1), '.', '');
            if (str_contains($t, '.') && str_ends_with(rtrim($t, '0'), '.')) $t = explode('.', $t)[0];
            $t = str_replace('.', ',', $t);
        }
        return $t . ($m[3] === '%' ? '%' : '');
    }, $desc);
    return str_replace('\\n', "\n", preg_replace('/\{F#([^}]*)\}\{M#([^}]*)\}/u', '$1/$2', $text));
}

/** Fähigkeit eines Lichtkegels als JSON-Text {"name", "text": [S1..S5]}, oder null. */
function lc_passive_json(string $lcId): ?string {
    $rank = de_source('light_cone_ranks.json')[$lcId] ?? null;
    if (!$rank || empty($rank['params'])) return null;
    return json_encode(['name' => $rank['skill'], 'text' => array_map(fn($p) => render_rank($rank['desc'], $p), $rank['params'])], JSON_UNESCAPED_UNICODE);
}

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
    // Gleiche Namen in der Quelle (z. B. zwei "March 7th" mit verschiedenem Pfad): die kleinste ID behält den Namen,
    // die übrigen bekommen den Pfad als Zusatz, weil der Name in der Datenbank eindeutig sein muss
    $firstId = [];
    $ids = array_keys($chars);
    sort($ids, SORT_NUMERIC);
    foreach ($ids as $cid) $firstId[$chars[$cid]['name']] ??= (string)$cid;
    foreach ($chars as $id => $c) {
        $path = PATHS[$c['path']] ?? null;
        $element = ELEMENTS[$c['element']] ?? null;
        $name = $c['name'];
        if ($name !== '{NICKNAME}' && $firstId[$name] !== (string)$id && $path) $name = "$name ($path)";
        if ($name === '{NICKNAME}') $name = $path ? 'Trailblazer (' . preg_replace('/^(Die|Der|Das)\s+/u', '', $path) . ')' : $name;   // je Pfad ein Eintrag
        $key = mb_strtolower($name);
        if (isset($have['characters'][$key]) || isset($seen[$key])) continue;
        $seen[$key] = true;
        if (!$path || !$element) { $skipped[] = "Charakter $name: unbekannter Pfad oder Element"; continue; }
        $new['characters'][] = ['name' => $name, 'element' => $element, 'path' => $path, 'rarity' => (int)$c['rarity'],
            'icon_path' => 'assets/images/characters/' . slug($name) . '.png', 'src' => "icon/avatar/$id.png"];
    }
    foreach ($lcs as $id => $l) {
        if (isset($have['light_cones'][mb_strtolower($l['name'])])) continue;
        $path = PATHS[$l['path']] ?? null;
        if (!$path) { $skipped[] = "Lichtkegel {$l['name']}: unbekannter Pfad"; continue; }
        $new['light_cones'][] = ['name' => $l['name'], 'path' => $path, 'rarity' => (int)$l['rarity'],
            'icon_path' => 'assets/images/light_cones/' . slug($l['name']) . '.png', 'passive_json' => lc_passive_json((string)$id), 'src' => $l['icon']];
    }
    foreach ($sets as $id => $s) {
        if (isset($have['relic_sets'][mb_strtolower($s['name'])])) continue;
        $desc = de_source('relic_sets.json')[(string)$id]['desc'] ?? ($s['desc'] ?? []);   // Boni auf Deutsch
        $new['relic_sets'][] = ['name' => $s['name'], 'kind' => ((int)$id >= 300) ? 'Ornament' : 'Relikt',
            'bonus_2pc' => $desc[0] ?? null, 'bonus_4pc' => $desc[1] ?? null,
            'icon_path' => 'assets/images/relic_sets/' . slug($s['name']) . '.png', 'src' => $s['icon']];
    }
    return [$new, $skipped];
}

function public_view(array $new): array {
    $out = [];
    foreach ($new as $group => $items) {
        $out[$group] = array_map(fn($e) => array_diff_key($e, ['src' => 1, 'icon_path' => 1, 'bonus_2pc' => 1, 'bonus_4pc' => 1, 'passive_json' => 1]), $items);
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
    $hasPassive = (bool)$pdo->query("SHOW COLUMNS FROM light_cones LIKE 'passive_json'")->fetch();
    $stmts = [
        'characters' => $pdo->prepare("INSERT IGNORE INTO characters (name, element, path, rarity, icon_path) VALUES (?, ?, ?, ?, ?)"),
        // Ohne die Spalte passive_json (Migration 007 fehlt noch) werden Lichtkegel ohne Fähigkeit eingetragen
        'light_cones' => $pdo->prepare($hasPassive
            ? "INSERT IGNORE INTO light_cones (name, path, rarity, icon_path, passive_json) VALUES (?, ?, ?, ?, ?)"
            : "INSERT IGNORE INTO light_cones (name, path, rarity, icon_path) VALUES (?, ?, ?, ?)"),
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
                'light_cones' => $hasPassive ? [$e['name'], $e['path'], $e['rarity'], $e['icon_path'], $e['passive_json']] : [$e['name'], $e['path'], $e['rarity'], $e['icon_path']],
                'relic_sets' => [$e['name'], $e['kind'], $e['bonus_2pc'], $e['bonus_4pc'], $e['icon_path']],
            };
            $stmts[$group]->execute($args);
            $added += $stmts[$group]->rowCount();
            $state = save_image(SRC . $e['src'], $e['icon_path']);
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
