-- Relikt-Sets am Build (wie im Genshin-Manager): drei neue Spalten in user_characters. Ältere Builds ohne Werte
-- leitet der Server beim Laden aus den Sets der Slots ab, es sind keine weiteren Daten umzustellen.
-- Reihenfolge: erst diese Migration (python3 tools/remote_sql.py --ddl web/migrations/011_relikt_sets_am_build.sql), dann deploy.sh.
ALTER TABLE user_characters ADD COLUMN relic_set VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE user_characters ADD COLUMN relic_set_2 VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE user_characters ADD COLUMN ornament_set VARCHAR(255) NOT NULL DEFAULT '';
