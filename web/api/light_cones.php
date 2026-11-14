<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';

try {
    try {
        $rows = $pdo->query("SELECT id, name, path, rarity, icon_path, passive_json FROM light_cones ORDER BY name ASC")->fetchAll();
    } catch (PDOException $e) {
        // Migration 007 (Spalte passive_json) fehlt noch: die Lichtkegel ohne Fähigkeit ausliefern
        error_log("light_cones.php: passive_json fehlt, bitte migrations/007_lichtkegel_faehigkeiten.sql ausführen: " . $e->getMessage());
        $rows = $pdo->query("SELECT id, name, path, rarity, icon_path FROM light_cones ORDER BY name ASC")->fetchAll();
    }
    foreach ($rows as &$r) {
        $r['rarity'] = (int)$r['rarity'];
        // Fähigkeit {name, text[S1..S5]} oder null
        $r['passive'] = json_decode((string)($r['passive_json'] ?? ''), true) ?: null;
        unset($r['passive_json']);
    }
    unset($r);
    echo json_encode($rows, JSON_UNESCAPED_UNICODE);
} catch (PDOException $e) {
    error_log("light_cones.php: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(["error" => "Fehler beim Laden der Lichtkegel."], JSON_UNESCAPED_UNICODE);
}
