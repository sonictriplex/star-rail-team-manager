-- Datenfehler: Zwei Charaktere hießen "March 7th" (Eis/Bewahrung und Imaginär/Jagd), der Name ist aber eindeutig.
-- Die Datenbank hielt nur die Jagd-Version. Außerdem blieb "Trailblazer (Erinnerung)" aus der Zeit vor der
-- Umbenennung übrig. Beides war vor dem Einspielen in keinem Team und keinem Build in Verwendung.
UPDATE characters SET element = 'Eis', path = 'Die Bewahrung', icon_path = 'assets/images/char_march_7th.png' WHERE name = 'March 7th';
INSERT IGNORE INTO characters (name, element, path, rarity, icon_path) VALUES
  ('March 7th (Die Jagd)', 'Imaginär', 'Die Jagd', 4, 'assets/images/char_march_7th_die_jagd.png');
DELETE FROM characters WHERE name = 'Trailblazer (Erinnerung)'
  AND NOT EXISTS (SELECT 1 FROM team_members WHERE char_name = 'Trailblazer (Erinnerung)')
  AND NOT EXISTS (SELECT 1 FROM user_characters WHERE char_name = 'Trailblazer (Erinnerung)');
