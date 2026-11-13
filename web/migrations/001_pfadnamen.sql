-- Offizielle deutsche Pfadnamen: Nichtsein -> Nichtigkeit, Fülle -> Überfluss.
-- Vor dem erneuten Einspielen von seed_stammdaten.sql und vor dem Deploy ausführen.
ALTER TABLE characters MODIFY path ENUM('Zerstörung','Jagd','Erudition','Harmonie','Nichtsein','Nichtigkeit','Bewahrung','Fülle','Überfluss','Erinnerung','Hochgefühl') NOT NULL;
ALTER TABLE light_cones MODIFY path ENUM('Zerstörung','Jagd','Erudition','Harmonie','Nichtsein','Nichtigkeit','Bewahrung','Fülle','Überfluss','Erinnerung','Hochgefühl') NOT NULL;
UPDATE characters SET path = 'Nichtigkeit' WHERE path = 'Nichtsein';
UPDATE characters SET path = 'Überfluss'   WHERE path = 'Fülle';
UPDATE light_cones SET path = 'Nichtigkeit' WHERE path = 'Nichtsein';
UPDATE light_cones SET path = 'Überfluss'   WHERE path = 'Fülle';
ALTER TABLE characters MODIFY path ENUM('Zerstörung','Jagd','Erudition','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Erinnerung','Hochgefühl') NOT NULL;
ALTER TABLE light_cones MODIFY path ENUM('Zerstörung','Jagd','Erudition','Harmonie','Nichtigkeit','Bewahrung','Überfluss','Erinnerung','Hochgefühl') NOT NULL;
