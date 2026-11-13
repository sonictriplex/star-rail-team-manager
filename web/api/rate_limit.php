<?php
// api/rate_limit.php
// Begrenzung von Anmelde- und Registrierungsversuchen (Schutz vor Durchprobieren von Passwörtern und vor
// massenhaftem Anlegen von Konten). Gezählt wird in der Tabelle auth_attempts (migrations/002_auth_attempts.sql).
//
// Grundsätze:
// - Bei Anmeldungen zählen nur FEHLversuche, pro Benutzername und pro IP-Adresse. Eine erfolgreiche Anmeldung
//   setzt den Zähler des Benutzernamens zurück. Gesperrt wird auch bei richtigem Passwort, bis die Sperre abläuft.
// - Registrierungen zählen alle Versuche, pro IP-Adresse und insgesamt.
// - Ein Benutzername, den es nicht gibt, wird genauso gezählt und gesperrt wie ein vorhandener (kein Erraten
//   von Konten über das Sperrverhalten).
// - Gespeichert werden nur SHA-256-Werte von IP und Name, nie Klartext; Zeilen werden nach spätestens 2 Stunden gelöscht.
// - Fällt die Zählung aus (z. B. Tabelle fehlt noch), wird NICHT gesperrt: Die Anmeldung soll nie an diesem Schutz scheitern.

const RL_LOGIN_WINDOW        = 900;    // Sekunden (15 Minuten)
const RL_LOGIN_MAX_PER_USER  = 8;      // Fehlversuche je Benutzername im Fenster
const RL_LOGIN_MAX_PER_IP    = 15;     // Fehlversuche je IP im Fenster (großzügiger: gemeinsame Netze, Mobilfunk)
const RL_REGISTER_WINDOW     = 3600;   // Sekunden (1 Stunde)
const RL_REGISTER_MAX_PER_IP = 5;      // Registrierungsversuche je IP im Fenster
const RL_REGISTER_MAX_TOTAL  = 60;     // Registrierungsversuche insgesamt im Fenster
const RL_KEEP_SECONDS        = 7200;   // Aufräumen: ältere Zeilen werden gelöscht

/**
 * Die IP-Adresse des Clients. Hinter einem Reverse-Proxy (nginx) steht in REMOTE_ADDR die Adresse des Proxys;
 * dann gilt X-Real-IP bzw. das erste X-Forwarded-For. Diese Header werden NUR beachtet, wenn die Verbindung von
 * einer lokalen/privaten Adresse kommt (also vom eigenen Proxy), sonst könnte jeder sie fälschen.
 * Leerer Rückgabewert = Client nicht bestimmbar; dann gibt es keine IP-Begrenzung (nur die je Benutzername).
 */
function rl_client_ip(): string {
    $remote = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    $isPublic = filter_var($remote, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE) !== false;
    if ($isPublic) return $remote;

    foreach (['HTTP_X_REAL_IP', 'HTTP_X_FORWARDED_FOR'] as $header) {
        $value = trim(explode(',', (string)($_SERVER[$header] ?? ''))[0]);
        if ($value !== '' && filter_var($value, FILTER_VALIDATE_IP) !== false) return $value;
    }
    return '';
}

function rl_subject(string $kind, string $value): string {
    return hash('sha256', $kind . ':' . mb_strtolower(trim($value)));
}

/** Führt eine Abfrage aus; bei jedem Problem (auch stillen Fehlern) wird eine Ausnahme geworfen. */
function rl_run(PDO $pdo, string $sql, array $params = []): PDOStatement {
    $stmt = $pdo->prepare($sql);
    if ($stmt === false || $stmt->execute($params) === false) {
        throw new RuntimeException('Abfrage fehlgeschlagen');
    }
    return $stmt;
}

/** Löscht Zeilen, die für kein Fenster mehr gebraucht werden. */
function rl_cleanup(PDO $pdo): void {
    rl_run($pdo, "DELETE FROM auth_attempts WHERE created_at < NOW() - INTERVAL " . (int)RL_KEEP_SECONDS . " SECOND");
}

function rl_record(PDO $pdo, string $scope, string $subject): void {
    rl_run($pdo, "INSERT INTO auth_attempts (scope, subject, created_at) VALUES (?, ?, NOW())", [$scope, $subject]);
    if (random_int(1, 25) === 1) rl_cleanup($pdo);   // gelegentlich aufräumen
}

/** Sekunden bis zur nächsten erlaubten Anfrage; 0 = frei. */
function rl_wait(PDO $pdo, string $scope, string $subject, int $window, int $max): int {
    $where = "scope = ? AND subject = ? AND created_at > NOW() - INTERVAL " . (int)$window . " SECOND";
    $count = (int)rl_run($pdo, "SELECT COUNT(*) FROM auth_attempts WHERE $where", [$scope, $subject])->fetchColumn();
    if ($count < $max) return 0;

    // Frei wird es, sobald so viele alte Einträge abgelaufen sind, dass wieder unter dem Limit gezählt wird
    $offset = $count - $max;
    $sql = "SELECT TIMESTAMPDIFF(SECOND, NOW(), created_at + INTERVAL " . (int)$window . " SECOND)
            FROM auth_attempts WHERE $where ORDER BY created_at ASC LIMIT 1 OFFSET " . (int)$offset;
    $wait = rl_run($pdo, $sql, [$scope, $subject])->fetchColumn();
    return max(1, (int)$wait);
}

/** Antwortet mit 429 und beendet die Anfrage. */
function rl_block(int $seconds, string $what): void {
    $minutes = (int)ceil($seconds / 60);
    http_response_code(429);
    header('Retry-After: ' . $seconds);
    $when = $minutes <= 1 ? 'in etwa einer Minute' : "in $minutes Minuten";
    echo json_encode(["error" => "Zu viele $what. Bitte versuche es $when erneut."], JSON_UNESCAPED_UNICODE);
    exit();
}

// --- Anmeldung (Session-Login und Token-Login) ---

/** Sekunden, die diese Anmeldung noch gesperrt ist (0 = frei). Bei Problemen der Zählung: 0. */
function rl_login_wait(PDO $pdo, string $username): int {
    try {
        $wait = rl_wait($pdo, 'login_user', rl_subject('user', $username), RL_LOGIN_WINDOW, RL_LOGIN_MAX_PER_USER);
        $ip = rl_client_ip();
        if ($ip !== '') {
            $wait = max($wait, rl_wait($pdo, 'login_ip', rl_subject('ip', $ip), RL_LOGIN_WINDOW, RL_LOGIN_MAX_PER_IP));
        }
        return $wait;
    } catch (Throwable $e) {
        error_log('rate_limit login_wait: ' . $e->getMessage());
        return 0;
    }
}

/** Prüft vor der Passwortprüfung, ob gesperrt ist; beendet die Anfrage mit 429, wenn ja. */
function rl_login_gate(PDO $pdo, string $username): void {
    $wait = rl_login_wait($pdo, $username);
    if ($wait > 0) rl_block($wait, 'Anmeldeversuche');
}

function rl_login_failed(PDO $pdo, string $username): void {
    try {
        rl_record($pdo, 'login_user', rl_subject('user', $username));
        $ip = rl_client_ip();
        if ($ip !== '') rl_record($pdo, 'login_ip', rl_subject('ip', $ip));
    } catch (Throwable $e) {
        error_log('rate_limit login_failed: ' . $e->getMessage());
    }
}

function rl_login_succeeded(PDO $pdo, string $username): void {
    try {
        rl_run($pdo, "DELETE FROM auth_attempts WHERE scope = 'login_user' AND subject = ?", [rl_subject('user', $username)]);
    } catch (Throwable $e) {
        error_log('rate_limit login_succeeded: ' . $e->getMessage());
    }
}

// --- Registrierung ---

/**
 * Zählt einen Registrierungsversuch, wenn er erlaubt ist, und gibt die Wartezeit zurück (0 = erlaubt).
 * Gesperrte Versuche werden nicht mitgezählt, damit die Sperre nicht endlos verlängert wird.
 */
function rl_register_attempt(PDO $pdo): int {
    try {
        $all = rl_subject('all', 'register');
        $wait = rl_wait($pdo, 'register_all', $all, RL_REGISTER_WINDOW, RL_REGISTER_MAX_TOTAL);
        $ip = rl_client_ip();
        if ($ip !== '') {
            $wait = max($wait, rl_wait($pdo, 'register_ip', rl_subject('ip', $ip), RL_REGISTER_WINDOW, RL_REGISTER_MAX_PER_IP));
        }
        if ($wait === 0) {
            rl_record($pdo, 'register_all', $all);
            if ($ip !== '') rl_record($pdo, 'register_ip', rl_subject('ip', $ip));
        }
        return $wait;
    } catch (Throwable $e) {
        error_log('rate_limit register_attempt: ' . $e->getMessage());
        return 0;
    }
}

/** Prüft und zählt einen Registrierungsversuch; beendet die Anfrage mit 429, wenn das Limit erreicht ist. */
function rl_register_gate(PDO $pdo): void {
    $wait = rl_register_attempt($pdo);
    if ($wait > 0) rl_block($wait, 'Registrierungen');
}
