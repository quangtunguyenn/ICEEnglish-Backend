-- Apply before deploying the application. Existing attempts retain their original ordering.
ALTER TABLE exams ADD COLUMN IF NOT EXISTS shuffle_parts boolean NOT NULL DEFAULT false;
ALTER TABLE exam_parts ADD COLUMN IF NOT EXISTS fixed_position boolean NOT NULL DEFAULT false;
ALTER TABLE exam_parts ADD COLUMN IF NOT EXISTS shuffle_group varchar(255);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS part_order jsonb;
