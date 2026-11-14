-- Namen der einzelnen Relikt-Teile (z. B. "Vielgesichtige Maske des Diebs") für die Suche und den Tooltip: neue Spalte relic_sets.pieces_json.
-- Ausführen mit: python3 tools/remote_sql.py --ddl web/migrations/010_relikt_teile.sql
-- Danach web/seed_stammdaten.sql einspielen (füllt die Spalte). Ohne die Spalte fehlt nur die Suche nach Teilen.
ALTER TABLE relic_sets ADD COLUMN pieces_json MEDIUMTEXT NULL;
