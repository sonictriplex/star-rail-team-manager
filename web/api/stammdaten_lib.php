<?php
// Gemeinsame Helfer für stammdaten.php (Abruf der Datenquelle, Bilder speichern). Nur für Admins.

/** Lädt eine URL (HTTPS) und gibt den Inhalt zurück; wirft bei Fehlern eine Exception. */
function fetch_url(string $url, int $timeout = 60): string {
    if (function_exists('curl_init')) {
        $ch = curl_init($url);
        curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_FOLLOWLOCATION => true, CURLOPT_TIMEOUT => $timeout,
            CURLOPT_USERAGENT => 'stammdaten-update', CURLOPT_MAXFILESIZE => 20 * 1024 * 1024]);
        $body = curl_exec($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);
        if ($body === false || $code !== 200) throw new RuntimeException("Abruf fehlgeschlagen ($code $err): $url", $code);
        return $body;
    }
    $ctx = stream_context_create(['http' => ['timeout' => $timeout, 'header' => "User-Agent: stammdaten-update\r\n"]]);
    $body = @file_get_contents($url, false, $ctx);
    if ($body === false) throw new RuntimeException("Abruf fehlgeschlagen: $url");
    return $body;
}

function fetch_json(string $url): array {
    $data = json_decode(fetch_url($url), true);
    if (!is_array($data)) throw new RuntimeException("Ungültige Antwort der Datenquelle: $url");
    return $data;
}

/**
 * Speichert ein Bild (nur PNG) unter assets/images/<Typ>/, falls es dort noch nicht liegt.
 * Rückgabe: 'vorhanden', 'geladen' oder eine Fehlermeldung (beginnt mit 'FEHLER').
 */
function save_image(string $url, string $iconPath): string {
    // $iconPath wie in der DB: assets/images/<Typ>/<name>.png; Typ nur aus der festen Liste
    if (!preg_match('#^assets/images/(characters|light_cones|relic_sets)/([a-z0-9_]+\.png)$#', $iconPath, $m)) return 'FEHLER: ungültiger Bildpfad';
    $dir = __DIR__ . '/../assets/images/' . $m[1];
    $target = $dir . '/' . $m[2];
    if (is_file($target)) return 'vorhanden';
    if (!is_dir($dir) || !is_writable($dir)) return 'FEHLER: Bilder-Ordner nicht beschreibbar';
    try {
        $bytes = fetch_url($url, 30);
    } catch (Throwable $e) {
        return 'FEHLER: Download';
    }
    if (strncmp($bytes, "\x89PNG\r\n\x1a\n", 8) !== 0) return 'FEHLER: kein PNG';
    $tmp = $target . '.tmp' . bin2hex(random_bytes(4));
    if (file_put_contents($tmp, $bytes) === false || !rename($tmp, $target)) { @unlink($tmp); return 'FEHLER: Schreiben'; }
    return 'geladen';
}

/** Erlaubt Änderungen nur per fetch aus der Seite (schützt vor fremden Formularen). */
function require_ajax_post(): array {
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST' || ($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '') !== 'fetch') {
        http_response_code(400);
        echo json_encode(["error" => "Ungültige Anfrage."], JSON_UNESCAPED_UNICODE);
        exit();
    }
    $in = json_decode(file_get_contents('php://input'), true);
    return is_array($in) ? $in : [];
}

function norm_name(string $n): string {
    return mb_strtolower(trim(preg_replace('/[„“”"\'’]/u', '', $n)));
}
