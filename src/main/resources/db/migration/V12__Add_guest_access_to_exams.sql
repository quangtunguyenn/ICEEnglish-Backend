ALTER TABLE exams ADD COLUMN IF NOT EXISTS guest_accessible boolean NOT NULL DEFAULT false;

UPDATE exams
SET guest_accessible = true
WHERE id IN (
    '1ecd0981-9a4e-41f8-a653-be1f3c8a930e',
    '048cd3bb-b848-4f05-ac30-f973f8d916c2'
);
