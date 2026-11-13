-- Schema des Honkai: Star Rail Team-Managers (MySQL / MariaDB).
-- Entwurf nach dem Muster des Genshin-Managers. Noch nicht gegen eine Datenbank getestet.
-- Bewusst weggelassen: Spuren (Traces), Technik, Stat-Summen, Spielmodi (MoC / PF / AS).

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stammdaten (öffentlich, nur lesbar)
CREATE TABLE IF NOT EXISTS characters (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(255) NOT NULL UNIQUE,
    element   ENUM('Physisch','Feuer','Eis','Blitz','Wind','Quantum','Imaginär') NOT NULL,
    path      ENUM('Zerstörung','Jagd','Erudition','Harmonie','Nichtsein','Bewahrung','Fülle','Erinnerung','Hochgefühl') NOT NULL,
    rarity    TINYINT NOT NULL,                 -- 4 oder 5
    icon_path VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS light_cones (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(255) NOT NULL UNIQUE,
    path      ENUM('Zerstörung','Jagd','Erudition','Harmonie','Nichtsein','Bewahrung','Fülle','Erinnerung','Hochgefühl') NOT NULL,
    rarity    TINYINT NOT NULL,                 -- 3 bis 5
    icon_path VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Relikt-Sets (2er und 4er Bonus) und Planar-Ornamente (nur 2er Bonus)
CREATE TABLE IF NOT EXISTS relic_sets (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(255) NOT NULL UNIQUE,
    kind      ENUM('Relikt','Ornament') NOT NULL,
    bonus_2pc TEXT,
    bonus_4pc TEXT,                             -- bei Ornamenten NULL
    icon_path VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Nutzerdaten: ein Build pro Nutzer und Charakter (gilt in allen Teams)
CREATE TABLE IF NOT EXISTS user_characters (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL,
    char_name      VARCHAR(255) NOT NULL,
    level          TINYINT NOT NULL DEFAULT 80,   -- 1 bis 80
    eidolon        TINYINT NOT NULL DEFAULT 0,    -- 0 bis 6
    talent_basic   TINYINT NOT NULL DEFAULT 1,
    talent_skill   TINYINT NOT NULL DEFAULT 1,
    talent_ult     TINYINT NOT NULL DEFAULT 1,
    talent_talent  TINYINT NOT NULL DEFAULT 1,
    light_cone     VARCHAR(255) NOT NULL DEFAULT '',
    lc_level       TINYINT NOT NULL DEFAULT 80,   -- 1 bis 80
    lc_superimpose TINYINT NOT NULL DEFAULT 1,    -- S1 bis S5
    -- Slots: Kopf, Hände, Körper, Füße, Planarsphäre, Verbindungsseil
    -- je Slot: {set, rarity, level, main, substats: [{stat, val} x4]}
    relics_json    TEXT,
    UNIQUE KEY uq_user_char (user_id, char_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teams (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    user_id  INT NOT NULL,
    name     VARCHAR(255) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_user_team (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS team_members (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    team_id    INT NOT NULL,
    char_name  VARCHAR(255) NOT NULL,
    slot_order TINYINT NOT NULL,                -- 0 bis 3
    UNIQUE KEY uq_team_slot (team_id, slot_order),
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Token-Login für Apps
CREATE TABLE IF NOT EXISTS api_tokens (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    token_hash   CHAR(64) NOT NULL,
    device       VARCHAR(100) NOT NULL DEFAULT '',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NULL,
    expires_at   DATETIME NOT NULL,
    UNIQUE KEY uq_api_tokens_hash (token_hash),
    KEY idx_api_tokens_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Begrenzung von Anmelde- und Registrierungsversuchen
CREATE TABLE IF NOT EXISTS auth_attempts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope      VARCHAR(20) NOT NULL,
    subject    CHAR(64)    NOT NULL,
    created_at DATETIME    NOT NULL,
    KEY idx_auth_attempts_lookup (scope, subject, created_at),
    KEY idx_auth_attempts_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
