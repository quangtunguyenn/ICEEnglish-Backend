ALTER TABLE part_time_logs
    ALTER COLUMN time_spent_seconds DROP NOT NULL;

ALTER TABLE part_time_logs
    ADD COLUMN IF NOT EXISTS recommended_time_seconds integer;
