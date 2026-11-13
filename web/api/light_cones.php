<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

try {
    $rows = $pdo->query("SELECT id, name, path, rarity, icon_path FROM light_cones ORDER BY name ASC")->fetchAll();
    foreach ($rows as &$r) $r['rarity'] = (int)$r['rarity'];
    unset($r);
    echo json_encode($rows, JSON_UNESCAPED_UNICODE);
} catch (PDOException $e) {
    error_log("light_cones.php: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(["error" => "Fehler beim Laden der Lichtkegel."], JSON_UNESCAPED_UNICODE);
}
