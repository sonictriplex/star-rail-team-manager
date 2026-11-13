<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth_check.php';
require_once __DIR__ . '/rate_limit.php';

$action = $_GET['action'] ?? '';

/** Leert die Session-Daten, löscht das Cookie und zerstört die Session (nur wenn eine läuft). */
function destroy_session(): void {
    if (session_status() !== PHP_SESSION_ACTIVE) return;
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $p = session_get_cookie_params();
        setcookie(session_name(), '', [
            'expires'  => time() - 42000,
            'path'     => $p['path'],
            'domain'   => $p['domain'],
            'secure'   => $p['secure'],
            'httponly' => $p['httponly'],
            'samesite' => $p['samesite'] ?: 'Lax'
        ]);
    }
    session_destroy();
}

// 1. Status prüfen (Wer bin ich?)
if ($action === 'status') {
    $current = current_user();
    if ($current !== null) {
        echo json_encode([
            "logged_in" => true,
            "username"  => $current['username'],
            "user_id"   => $current['id']
        ], JSON_UNESCAPED_UNICODE);
    } else {
        echo json_encode(["logged_in" => false], JSON_UNESCAPED_UNICODE);
    }
    exit();
}

$data = json_decode(file_get_contents("php://input"), true) ?? [];

// 2. Registrierung
if ($action === 'register') {
    $username = trim($data['username'] ?? '');
    $password = $data['password'] ?? '';

    if (strlen($username) < 3 || strlen($password) < 6) {
        http_response_code(400);
        echo json_encode(["error" => "Benutzername (min. 3 Zeichen) oder Passwort (min. 6 Zeichen) zu kurz."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    // Begrenzung: höchstens wenige Registrierungsversuche je IP und Stunde (siehe rate_limit.php)
    rl_register_gate($pdo);

    $hash = password_hash($password, PASSWORD_DEFAULT);

    try {
        $stmt = $pdo->prepare("INSERT INTO users (username, password_hash) VALUES (?, ?)");
        $stmt->execute([$username, $hash]);

        // Neue Session-ID gegen Session-Fixation
        session_regenerate_id(true);

        $_SESSION['user_id'] = (int)$pdo->lastInsertId();
        $_SESSION['username'] = $username;

        echo json_encode(["success" => true, "username" => $username], JSON_UNESCAPED_UNICODE);
    } catch (PDOException $e) {
        if ($e->getCode() == 23000) { // Duplicate Entry
            http_response_code(409);
            echo json_encode(["error" => "Dieser Benutzername ist bereits vergeben."], JSON_UNESCAPED_UNICODE);
        } else {
            http_response_code(500);
            error_log("auth.php register: " . $e->getMessage());
            echo json_encode(["error" => "Registrierung fehlgeschlagen (Datenbankfehler)."], JSON_UNESCAPED_UNICODE);
        }
    }
    exit();
}

// 3. Login
if ($action === 'login') {
    $username = trim($data['username'] ?? '');
    $password = $data['password'] ?? '';

    rl_login_gate($pdo, $username);   // 429, wenn zu viele Fehlversuche (siehe rate_limit.php)

    $stmt = $pdo->prepare("SELECT id, username, password_hash FROM users WHERE username = ?");
    $stmt->execute([$username]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($user && password_verify($password, $user['password_hash'])) {
        rl_login_succeeded($pdo, $username);

        // Neue Session-ID gegen Session-Fixation
        session_regenerate_id(true);

        $_SESSION['user_id'] = (int)$user['id'];
        $_SESSION['username'] = $user['username'];

        echo json_encode(["success" => true, "username" => $user['username']], JSON_UNESCAPED_UNICODE);
    } else {
        rl_login_failed($pdo, $username);
        http_response_code(401);
        echo json_encode(["error" => "Benutzername oder Passwort falsch."], JSON_UNESCAPED_UNICODE);
    }
    exit();
}

// 4. Token-Login für Apps ohne Cookies (POST {username, password, device?})
//    Antwort: {token, expires_at, username}. Der Token wird nur als SHA-256-Hash gespeichert und
//    ist danach nicht mehr abrufbar. Er gilt TOKEN_LIFETIME_DAYS Tage und wird per
//    "Authorization: Bearer <token>" gesendet. Nur über HTTPS verwenden.
const TOKEN_LIFETIME_DAYS = 90;
const TOKEN_MAX_PER_USER = 10;

if ($action === 'token') {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        http_response_code(405);
        header('Allow: POST');
        echo json_encode(["error" => "Token nur per POST abrufbar."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    $username = trim($data['username'] ?? '');
    $password = $data['password'] ?? '';
    $device = mb_substr(trim((string)($data['device'] ?? '')), 0, 100);

    rl_login_gate($pdo, $username);   // 429, wenn zu viele Fehlversuche (siehe rate_limit.php)

    $stmt = $pdo->prepare("SELECT id, username, password_hash FROM users WHERE username = ?");
    $stmt->execute([$username]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user || !password_verify($password, $user['password_hash'])) {
        rl_login_failed($pdo, $username);
        http_response_code(401);
        echo json_encode(["error" => "Benutzername oder Passwort falsch."], JSON_UNESCAPED_UNICODE);
        exit();
    }
    rl_login_succeeded($pdo, $username);

    try {
        $token = bin2hex(random_bytes(32));
        $expires = date('Y-m-d H:i:s', time() + 86400 * TOKEN_LIFETIME_DAYS);

        // Abgelaufene Token aufräumen und die ältesten entfernen, wenn zu viele ausgestellt wurden
        $pdo->prepare("DELETE FROM api_tokens WHERE user_id = ? AND expires_at <= NOW()")->execute([$user['id']]);
        $pdo->prepare("
            DELETE FROM api_tokens WHERE user_id = ? AND id NOT IN (
                SELECT id FROM (SELECT id FROM api_tokens WHERE user_id = ? ORDER BY id DESC LIMIT " . (TOKEN_MAX_PER_USER - 1) . ") keep
            )
        ")->execute([$user['id'], $user['id']]);

        $pdo->prepare("INSERT INTO api_tokens (user_id, token_hash, device, expires_at) VALUES (?, ?, ?, ?)")
            ->execute([$user['id'], hash('sha256', $token), $device, $expires]);

        echo json_encode([
            "token"      => $token,
            "expires_at" => $expires,
            "username"   => $user['username'],
        ], JSON_UNESCAPED_UNICODE);
    } catch (Exception $e) {
        http_response_code(500);
        error_log("auth.php token: " . $e->getMessage());
        echo json_encode(["error" => "Token konnte nicht erstellt werden."], JSON_UNESCAPED_UNICODE);
    }
    exit();
}

// 5. Logout
if ($action === 'logout') {
    // Nur per POST, damit ein Logout nicht über einen fremden Link/Bild ausgelöst werden kann
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        http_response_code(405);
        header('Allow: POST');
        echo json_encode(["error" => "Logout nur per POST erlaubt."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    // Mit Bearer-Token: genau diesen Token widerrufen (es gibt keine Session/kein Cookie)
    if (($GLOBALS['auth_token'] ?? '') !== '') {
        $pdo->prepare("DELETE FROM api_tokens WHERE token_hash = ?")->execute([hash('sha256', $GLOBALS['auth_token'])]);
        echo json_encode(["success" => true], JSON_UNESCAPED_UNICODE);
        exit();
    }

    destroy_session();

    echo json_encode(["success" => true], JSON_UNESCAPED_UNICODE);
    exit();
}

// 6. Konto löschen (POST {password}): entfernt den Nutzer samt Teams, Builds und allen Token.
//    Das Passwort muss erneut stimmen; falsche Eingaben zählen wie beim Login für die Begrenzung.
//    Bewusst 403 statt 401 bei falschem Passwort: 401 bedeutet für die Apps "Sitzung abgelaufen".
if ($action === 'delete_account') {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        http_response_code(405);
        header('Allow: POST');
        echo json_encode(["error" => "Konto löschen nur per POST erlaubt."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    $current = current_user();
    if ($current === null) {
        http_response_code(401);
        echo json_encode(["error" => "Nicht eingeloggt"], JSON_UNESCAPED_UNICODE);
        exit();
    }

    $password = (string)($data['password'] ?? '');
    rl_login_gate($pdo, $current['username']);

    $stmt = $pdo->prepare("SELECT password_hash FROM users WHERE id = ?");
    $stmt->execute([$current['id']]);
    $hash = $stmt->fetchColumn();

    if ($hash === false || !password_verify($password, $hash)) {
        rl_login_failed($pdo, $current['username']);
        http_response_code(403);
        echo json_encode(["error" => "Passwort falsch."], JSON_UNESCAPED_UNICODE);
        exit();
    }
    rl_login_succeeded($pdo, $current['username']);

    try {
        $pdo->beginTransaction();
        // Mitglieder explizit löschen, ohne sich auf ON DELETE CASCADE der Produktivdatenbank zu verlassen
        $pdo->prepare("DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE user_id = ?)")->execute([$current['id']]);
        $pdo->prepare("DELETE FROM teams WHERE user_id = ?")->execute([$current['id']]);
        $pdo->prepare("DELETE FROM user_characters WHERE user_id = ?")->execute([$current['id']]);
        $pdo->prepare("DELETE FROM api_tokens WHERE user_id = ?")->execute([$current['id']]);
        $pdo->prepare("DELETE FROM users WHERE id = ?")->execute([$current['id']]);
        $pdo->commit();
    } catch (Exception $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        http_response_code(500);
        error_log("auth.php delete_account: " . $e->getMessage());
        echo json_encode(["error" => "Konto konnte nicht gelöscht werden."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    destroy_session();
    echo json_encode(["success" => true], JSON_UNESCAPED_UNICODE);
    exit();
}
?>
