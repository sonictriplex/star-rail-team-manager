-- Fähigkeiten der Lichtkegel (Tooltips): neue Spalte light_cones.passive_json mit {"name": ..., "text": [S1 ... S5]}.
-- Ausführen mit: python3 tools/remote_sql.py --ddl web/migrations/007_lichtkegel_faehigkeiten.sql
-- Danach web/seed_stammdaten.sql einspielen (füllt die Spalte und die deutschen Set-Boni). Ohne die Spalte fehlen nur die Tooltips.
ALTER TABLE light_cones ADD COLUMN passive_json MEDIUMTEXT NULL;
