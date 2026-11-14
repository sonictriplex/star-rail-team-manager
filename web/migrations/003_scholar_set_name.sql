-- Korrektur: Beim Umbenennen der Pfade wurde versehentlich auch das Relikt-Set "Scholar Lost in Erudition"
-- zu "Scholar Lost in Gelehrsamkeit" geändert. Der Seed hat diesen falschen Namen als zweiten Eintrag angelegt.
-- 1. Gespeicherte Builds auf den richtigen Namen zurücksetzen
UPDATE user_characters SET relics_json = REPLACE(relics_json, 'Scholar Lost in Gelehrsamkeit', 'Scholar Lost in Erudition')
  WHERE relics_json LIKE '%Scholar Lost in Gelehrsamkeit%';
-- 2. Den falschen Eintrag entfernen (der richtige bleibt bestehen)
DELETE FROM relic_sets WHERE name = 'Scholar Lost in Gelehrsamkeit';
-- Kontrolle: genau 1 Zeile (Erudition), 0 Zeilen (Gelehrsamkeit)
SELECT name FROM relic_sets WHERE name LIKE 'Scholar Lost in%';
