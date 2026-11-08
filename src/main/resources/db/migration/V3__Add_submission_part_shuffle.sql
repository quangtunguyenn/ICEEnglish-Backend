ALTER TABLE exams ADD COLUMN IF NOT EXISTS shuffle_parts boolean NOT NULL DEFAULT false;
ALTER TABLE exam_parts ADD COLUMN IF NOT EXISTS fixed_position boolean NOT NULL DEFAULT false;
ALTER TABLE exam_parts ADD COLUMN IF NOT EXISTS shuffle_group varchar(255);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS part_order jsonb;
-- Persist shuffle configuration and the selected order for each submission.
ALTER TABLE exams ADD COLUMN IF NOT EXISTS shuffle_parts boolean NOT NULL DEFAULT false;
ALTER TABLE exam_parts ADD COLUMN IF NOT EXISTS fixed_position boolean NOT NULL DEFAULT false;
ALTER TABLE exam_parts ADD COLUMN IF NOT EXISTS shuffle_group varchar(255);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS part_order jsonb;