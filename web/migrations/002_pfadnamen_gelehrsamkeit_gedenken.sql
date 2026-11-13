-- Offizielle deutsche Pfadnamen: Erudition -> Gelehrsamkeit, Erinnerung -> Gedenken.
-- Reihenfolge: 1. dieses Skript, 2. seed_stammdaten.sql (wiederholbar), 3. Deploy.
ALTER TABLE characters MODIFY path ENUM('Zerstörung','Jagd','Erudition','Gelehrsamkeit','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Erinnerung','Gedenken','Hochgefühl') NOT NULL;
ALTER TABLE light_cones MODIFY path ENUM('Zerstörung','Jagd','Erudition','Gelehrsamkeit','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Erinnerung','Gedenken','Hochgefühl') NOT NULL;
UPDATE characters SET path = 'Gelehrsamkeit' WHERE path = 'Erudition';
UPDATE characters SET path = 'Gedenken' WHERE path = 'Erinnerung';
UPDATE light_cones SET path = 'Gelehrsamkeit' WHERE path = 'Erudition';
UPDATE light_cones SET path = 'Gedenken' WHERE path = 'Erinnerung';
ALTER TABLE characters MODIFY path ENUM('Zerstörung','Jagd','Gelehrsamkeit','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Gedenken','Hochgefühl') NOT NULL;
ALTER TABLE light_cones MODIFY path ENUM('Zerstörung','Jagd','Gelehrsamkeit','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Gedenken','Hochgefühl') NOT NULL;
-- Kontrolle: beide Abfragen müssen 0 liefern.
SELECT COUNT(*) FROM characters WHERE path = '';
SELECT COUNT(*) FROM light_cones WHERE path = '';
