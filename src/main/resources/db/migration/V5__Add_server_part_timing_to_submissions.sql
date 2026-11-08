-- The active part and its server-recorded start time let the backend calculate
-- per-part elapsed seconds without trusting a student's device clock.
ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS active_part_id uuid;

ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS active_part_started_at timestamp without time zone;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_submissions_active_part'
          AND conrelid = 'submissions'::regclass
    ) THEN
        ALTER TABLE submissions
            ADD CONSTRAINT fk_submissions_active_part
            FOREIGN KEY (active_part_id)
            REFERENCES exam_parts (id)
            ON DELETE SET NULL;
    END IF;
END
$$;
