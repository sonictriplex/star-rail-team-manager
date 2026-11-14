<?php
// api/auth_check.php
/**
 * Liest den API-Token aus der Anfrage (leer, wenn keiner gesendet wurde):
 * 1. "Authorization: Bearer <token>" (Standard)
 * 2. "X-Auth-Token: <token>" als Ausweichmöglichkeit. Viele Hoster (nginx + PHP-FPM, Apache mit CGI)
 *    reichen den Authorization-Header nicht an PHP durch; eigene Header kommen dagegen immer an.
 * Ein gültiger Bearer-Token hat Vorrang. Beide haben dasselbe Format (64 Zeichen, a-z A-Z 0-9).
 */
function bearer_token(): string {
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if ($h === '' && function_exists('getallheaders')) {
        foreach (getallheaders() as $k => $v) {
            if (strtolower($k) === 'authorization') { $h = $v; break; }
        }
    }
    if (preg_match('/^Bearer\s+([A-Za-z0-9]{64})$/', trim($h), $m)) return $m[1];

    $x = trim((string)($_SERVER['HTTP_X_AUTH_TOKEN'] ?? ''));
    return preg_match('/^[A-Za-z0-9]{64}$/', $x) ? $x : '';
}

// Clients mit Token (z. B. die Android-App) brauchen keine Session und kein Cookie.
$GLOBALS['auth_token'] = bearer_token();

if ($GLOBALS['auth_token'] === '' && session_status() === PHP_SESSION_NONE) {
    // Secure-Flag nur setzen, wenn die Verbindung wirklich über HTTPS läuft
    // (auch hinter einem Reverse-Proxy wie nginx erkennbar)
    $isHttps = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
    || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');

    session_set_cookie_params([
        'lifetime' => 86400 * 7, // Session 7 Tage gültig
        'path'     => '/',
        'secure'   => $isHttps,
        'httponly' => true,
        'samesite' => 'Lax'
    ]);
    session_start();
}

/**
 * Liefert den angemeldeten Nutzer als ['id' => int, 'username' => string] oder null.
 * Ein gesendeter Bearer-Token hat Vorrang; ist er ungültig oder abgelaufen, gilt man als nicht
 * eingeloggt (kein Rückfall auf eine Session). Voraussetzung: $pdo ist per db.php gesetzt.
 */
function current_user(): ?array {
    static $cached = false;
    if ($cached !== false) return $cached;

    $token = $GLOBALS['auth_token'] ?? '';
    if ($token !== '') {
        global $pdo;
        $stmt = $pdo->prepare("
            SELECT u.id, u.username FROM api_tokens t
            JOIN users u ON u.id = t.user_id
            WHERE t.token_hash = ? AND t.expires_at > NOW()
        ");
        $stmt->execute([hash('sha256', $token)]);
        $row = $stmt->fetch();
        if ($row) {
            $pdo->prepare("UPDATE api_tokens SET last_used_at = NOW() WHERE token_hash = ?")
                ->execute([hash('sha256', $token)]);
            return $cached = ['id' => (int)$row['id'], 'username' => (string)$row['username']];
        }
        return $cached = null;
    }

    if (isset($_SESSION['user_id'])) {
        return $cached = ['id' => (int)$_SESSION['user_id'], 'username' => (string)($_SESSION['username'] ?? '')];
    }
    return $cached = null;
}

/**
 * Prüft ob der Nutzer eingeloggt ist (Session oder Bearer-Token).
 * Gibt die user_id zurück oder bricht mit 401 Unauthorized ab.
 */
function require_login() {
    $user = current_user();
    if ($user === null) {
        http_response_code(401);
        echo json_encode(["error" => "Nicht eingeloggt"], JSON_UNESCAPED_UNICODE);
        exit();
    }
    return $user['id'];
}

/**
 * Admins stehen in api/admins.local.php (return ['benutzername', ...];, nicht im Git, wird nie hochgeladen)
 * oder in der Umgebungsvariable ADMIN_USERS (kommagetrennt). Ohne Eintrag gibt es keinen Admin.
 */
function is_admin(?array $user): bool {
    if ($user === null || $user['username'] === '') return false;
    $names = [];
    $file = __DIR__ . '/admins.local.php';
    if (is_file($file)) {
        $list = require $file;
        if (is_array($list)) $names = array_map('strval', $list);
    }
    $env = getenv('ADMIN_USERS');
    if ($env !== false && $env !== '') $names = array_merge($names, array_map('trim', explode(',', $env)));
    return in_array($user['username'], $names, true);
}

/** Bricht mit 403 ab, wenn der angemeldete Nutzer kein Admin ist. */
function require_admin(): void {
    if (!is_admin(current_user())) {
        http_response_code(403);
        echo json_encode(["error" => "Nur für Administratoren."], JSON_UNESCAPED_UNICODE);
        exit();
    }
}
?>
