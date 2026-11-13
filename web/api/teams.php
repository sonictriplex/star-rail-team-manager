<?php
header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth_check.php';
require_once __DIR__ . '/helpers.php';

// Erzwingt Session-Authentifizierung und liefert die ID des Nutzers
$user_id = require_login();
$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    try {
        // Teams des aktuell eingeloggten Benutzers abfragen
        $stmtTeams = $pdo->prepare("SELECT id, name FROM teams WHERE user_id = ? ORDER BY position ASC, id ASC");
        $stmtTeams->execute([$user_id]);
        $teams = $stmtTeams->fetchAll(PDO::FETCH_ASSOC);

        $result = new stdClass();

        foreach ($teams as $team) {
            $teamName = $team['name'];

            // Mitglieder des Teams mit den User-Builds DIESES Benutzers verknüpfen
            $stmtMembers = $pdo->prepare("
            SELECT
            tm.char_name,
            uc.level, uc.eidolon, uc.talent_basic, uc.talent_skill, uc.talent_ult, uc.talent_talent,
            uc.light_cone, uc.lc_level, uc.lc_superimpose, uc.relics_json,
            c.element, c.path, c.rarity, c.icon_path
            FROM team_members tm
            LEFT JOIN characters c ON tm.char_name = c.name
            LEFT JOIN user_characters uc ON (tm.char_name = uc.char_name AND uc.user_id = ?)
            WHERE tm.team_id = ?
            ORDER BY tm.slot_order ASC
            ");
            $stmtMembers->execute([$user_id, $team['id']]);

            $teamMembers = [];
            foreach ($stmtMembers->fetchAll() as $m) {
                $teamMembers[] = [
                    "character" => [
                        "name"      => $m['char_name'],
                        "element"   => $m['element'] ?? 'Physisch',
                        "path"      => $m['path'] ?? '',
                        "rarity"    => (int)($m['rarity'] ?? 4),
                        "icon_path" => $m['icon_path'] ?? null
                    ],
                    "saved" => build_from_row($m, $m['char_name'])
                ];
            }
            $result->$teamName = $teamMembers;
        }

        echo json_encode($result, JSON_UNESCAPED_UNICODE);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        error_log("teams.php GET: " . $e->getMessage());
        echo json_encode(["error" => "Fehler beim Laden der Teams."], JSON_UNESCAPED_UNICODE);
        exit();
    }

} elseif ($method === 'POST') {
    $data = json_decode(file_get_contents('php://input'), true) ?? [];
    // Reihenfolge der Teams speichern: {order: [Teamname, ...]}
    if (is_array($data['order'] ?? null)) {
        $pdo->beginTransaction();
        try {
            $stmtPos = $pdo->prepare("UPDATE teams SET position = ? WHERE user_id = ? AND name = ?");
            $pos = 0;
            foreach ($data['order'] as $n) {
                if (!is_string($n)) continue;
                $pos++;
                $stmtPos->execute([$pos, $user_id, $n]);
            }
            $pdo->commit();
            echo json_encode(["message" => "Reihenfolge gespeichert."], JSON_UNESCAPED_UNICODE);
        } catch (Exception $e) {
            $pdo->rollBack();
            http_response_code(500);
            error_log("teams.php ORDER: " . $e->getMessage());
            echo json_encode(["error" => "Fehler beim Speichern der Reihenfolge."], JSON_UNESCAPED_UNICODE);
        }
        exit();
    }

    $teamName = trim($data['name'] ?? '');
    $members = is_array($data['members'] ?? null) ? $data['members'] : [];

    if (empty($teamName)) {
        http_response_code(400);
        echo json_encode(["error" => "Teamname darf nicht leer sein."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    if (mb_strlen($teamName) > 255) {
        http_response_code(400);
        echo json_encode(["error" => "Teamname ist zu lang."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    if (count($members) > 4) {
        http_response_code(400);
        echo json_encode(["error" => "Ein Team darf maximal 4 Charaktere enthalten."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    $pdo->beginTransaction();
    try {
        // Team für diesen Nutzer anlegen oder das vorhandene verwenden – in EINEM Statement.
        // Dank UNIQUE (user_id, name) gibt es auch bei parallelen Requests (Doppelklick)
        // nie zwei gleiche Teams. LAST_INSERT_ID(id) liefert in beiden Fällen die Team-ID.
        // Die Zeile ist danach bis zum Commit gesperrt, ein paralleler Request wartet.
        // Ein neues Team kommt ans Ende der Reihenfolge (position), ein vorhandenes behält seinen Platz.
        $stmtTeam = $pdo->prepare("
        INSERT INTO teams (user_id, name, position)
        SELECT ?, ?, COALESCE(MAX(position), 0) + 1 FROM teams WHERE user_id = ?
        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
        ");
        $stmtTeam->execute([$user_id, $teamName, $user_id]);
        $teamId = (int)$pdo->lastInsertId();

        // Bisherige Mitglieder entfernen (bei einem neuen Team gibt es keine)
        $stmtDel = $pdo->prepare("DELETE FROM team_members WHERE team_id = ?");
        $stmtDel->execute([$teamId]);

        $seenChars = [];
        $slot = 0;

        // Charaktere und Builds für diesen Nutzer speichern
        foreach ($members as $m) {
            if (!is_array($m)) continue;

            $charName = trim($m['name'] ?? '');
            if ($charName === '') continue;

            // Doppelte Charaktere im selben Team überspringen
            if (isset($seenChars[$charName])) continue;
            $seenChars[$charName] = true;

            // 1. Charakter-Build in user_characters (user_id + char_name) aktualisieren
            $stmtChar = $pdo->prepare(BUILD_UPSERT_SQL);
            $stmtChar->execute(array_merge([$user_id, $charName], build_values($m)));

            // 2. Mitglied dem Team zuordnen
            $stmtMember = $pdo->prepare("INSERT INTO team_members (team_id, char_name, slot_order) VALUES (?, ?, ?)");
            $stmtMember->execute([$teamId, $charName, $slot]);
            $slot++;
        }

        $pdo->commit();
        echo json_encode(["message" => "Team '$teamName' erfolgreich gespeichert!"], JSON_UNESCAPED_UNICODE);
    } catch (Exception $e) {
        $pdo->rollBack();
        http_response_code(500);
        error_log("teams.php POST: " . $e->getMessage());
        echo json_encode(["error" => "Fehler beim Speichern des Teams."], JSON_UNESCAPED_UNICODE);
    }
    exit();

} elseif ($method === 'DELETE') {
    $teamName = trim($_GET['name'] ?? '');

    if (empty($teamName)) {
        http_response_code(400);
        echo json_encode(["error" => "Teamname zum Löschen fehlt."], JSON_UNESCAPED_UNICODE);
        exit();
    }

    try {
        $stmt = $pdo->prepare("DELETE FROM teams WHERE user_id = ? AND name = ?");
        $stmt->execute([$user_id, $teamName]);

        echo json_encode(["message" => "Team '$teamName' wurde erfolgreich gelöscht!"], JSON_UNESCAPED_UNICODE);
    } catch (Exception $e) {
        http_response_code(500);
        error_log("teams.php DELETE: " . $e->getMessage());
        echo json_encode(["error" => "Fehler beim Löschen des Teams."], JSON_UNESCAPED_UNICODE);
    }
    exit();
}
