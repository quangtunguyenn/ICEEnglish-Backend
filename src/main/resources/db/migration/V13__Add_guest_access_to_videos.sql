ALTER TABLE videos ADD COLUMN IF NOT EXISTS guest_accessible boolean NOT NULL DEFAULT false;

UPDATE videos
SET guest_accessible = true
WHERE id = '295d2ec6-dcf6-43d8-8804-edf15a6e3863';
