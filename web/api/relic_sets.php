<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

try {
    $rows = $pdo->query("SELECT id, name, kind, bonus_2pc, bonus_4pc, icon_path FROM relic_sets ORDER BY name ASC")->fetchAll();
    echo json_encode($rows, JSON_UNESCAPED_UNICODE);
} catch (PDOException $e) {
    error_log("relic_sets.php: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(["error" => "Fehler beim Laden der Relikt-Sets."], JSON_UNESCAPED_UNICODE);
}
