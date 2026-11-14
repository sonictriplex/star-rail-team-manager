<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

try {
    try {
        $rows = $pdo->query("SELECT id, name, kind, bonus_2pc, bonus_4pc, icon_path, pieces_json FROM relic_sets ORDER BY name ASC")->fetchAll();
    } catch (PDOException $e) {
        // Migration 010 (Spalte pieces_json) fehlt noch: die Sets ohne Teile-Namen ausliefern
        error_log("relic_sets.php: pieces_json fehlt, bitte migrations/010_relikt_teile.sql ausführen: " . $e->getMessage());
        $rows = $pdo->query("SELECT id, name, kind, bonus_2pc, bonus_4pc, icon_path FROM relic_sets ORDER BY name ASC")->fetchAll();
    }
    foreach ($rows as &$r) {
        // Teile {Slot: [Name, ...]} oder leeres Objekt
        $r['pieces'] = json_decode((string)($r['pieces_json'] ?? ''), true) ?: new stdClass();
        unset($r['pieces_json']);
    }
    unset($r);
    echo json_encode($rows, JSON_UNESCAPED_UNICODE);
} catch (PDOException $e) {
    error_log("relic_sets.php: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(["error" => "Fehler beim Laden der Relikt-Sets."], JSON_UNESCAPED_UNICODE);
}
