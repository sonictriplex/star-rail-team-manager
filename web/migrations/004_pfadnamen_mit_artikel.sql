-- Pfadnamen mit Artikel wie im Spiel: Die Zerstörung, Die Jagd, Die Gelehrsamkeit, Die Harmonie, Die Nichtigkeit,
-- Die Bewahrung, Der Überfluss, Das Gedenken, Das Hochgefühl. Ausführen mit: python3 tools/remote_sql.py --ddl <diese Datei>
-- (erst die Spalte um die neuen Werte erweitern, dann umschreiben, dann die alten Werte entfernen).
ALTER TABLE characters MODIFY path ENUM('Zerstörung','Jagd','Gelehrsamkeit','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Gedenken','Hochgefühl','Die Zerstörung','Die Jagd','Die Gelehrsamkeit','Die Harmonie','Die Nichtigkeit','Die Bewahrung','Der Überfluss','Das Gedenken','Das Hochgefühl') NOT NULL;
ALTER TABLE light_cones MODIFY path ENUM('Zerstörung','Jagd','Gelehrsamkeit','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Gedenken','Hochgefühl','Die Zerstörung','Die Jagd','Die Gelehrsamkeit','Die Harmonie','Die Nichtigkeit','Die Bewahrung','Der Überfluss','Das Gedenken','Das Hochgefühl') NOT NULL;
UPDATE characters SET path = 'Die Zerstörung' WHERE path = 'Zerstörung';
UPDATE characters SET path = 'Die Jagd' WHERE path = 'Jagd';
UPDATE characters SET path = 'Die Gelehrsamkeit' WHERE path = 'Gelehrsamkeit';
UPDATE characters SET path = 'Die Harmonie' WHERE path = 'Harmonie';
UPDATE characters SET path = 'Die Nichtigkeit' WHERE path = 'Nichtigkeit';
UPDATE characters SET path = 'Die Bewahrung' WHERE path = 'Bewahrung';
UPDATE characters SET path = 'Der Überfluss' WHERE path = 'Überfluss';
UPDATE characters SET path = 'Das Gedenken' WHERE path = 'Gedenken';
UPDATE characters SET path = 'Das Hochgefühl' WHERE path = 'Hochgefühl';
UPDATE light_cones SET path = 'Die Zerstörung' WHERE path = 'Zerstörung';
UPDATE light_cones SET path = 'Die Jagd' WHERE path = 'Jagd';
UPDATE light_cones SET path = 'Die Gelehrsamkeit' WHERE path = 'Gelehrsamkeit';
UPDATE light_cones SET path = 'Die Harmonie' WHERE path = 'Harmonie';
UPDATE light_cones SET path = 'Die Nichtigkeit' WHERE path = 'Nichtigkeit';
UPDATE light_cones SET path = 'Die Bewahrung' WHERE path = 'Bewahrung';
UPDATE light_cones SET path = 'Der Überfluss' WHERE path = 'Überfluss';
UPDATE light_cones SET path = 'Das Gedenken' WHERE path = 'Gedenken';
UPDATE light_cones SET path = 'Das Hochgefühl' WHERE path = 'Hochgefühl';
ALTER TABLE characters MODIFY path ENUM('Die Zerstörung','Die Jagd','Die Gelehrsamkeit','Die Harmonie','Die Nichtigkeit','Die Bewahrung','Der Überfluss','Das Gedenken','Das Hochgefühl') NOT NULL;
ALTER TABLE light_cones MODIFY path ENUM('Die Zerstörung','Die Jagd','Die Gelehrsamkeit','Die Harmonie','Die Nichtigkeit','Die Bewahrung','Der Überfluss','Das Gedenken','Das Hochgefühl') NOT NULL;
