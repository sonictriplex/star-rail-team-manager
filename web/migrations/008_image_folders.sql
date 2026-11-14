-- Bilder liegen jetzt nach Typ in Unterordnern (assets/images/characters|light_cones|relic_sets/<name>.png).
-- Einmal ausführen: python3 tools/remote_sql.py web/migrations/008_image_folders.sql (wiederholbar).
UPDATE characters SET icon_path = REPLACE(icon_path, 'assets/images/char_', 'assets/images/characters/') WHERE icon_path LIKE 'assets/images/char\_%';
UPDATE light_cones SET icon_path = REPLACE(icon_path, 'assets/images/lc_', 'assets/images/light_cones/') WHERE icon_path LIKE 'assets/images/lc\_%';
UPDATE relic_sets SET icon_path = REPLACE(icon_path, 'assets/images/relic_', 'assets/images/relic_sets/') WHERE icon_path LIKE 'assets/images/relic\_%';
