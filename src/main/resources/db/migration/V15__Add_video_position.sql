ALTER TABLE videos ADD COLUMN IF NOT EXISTS position varchar(32);
ALTER TABLE videos ADD COLUMN IF NOT EXISTS position_chapter integer;
ALTER TABLE videos ADD COLUMN IF NOT EXISTS position_lesson integer;

CREATE UNIQUE INDEX IF NOT EXISTS uq_videos_position
    ON videos (position)
    WHERE position IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_videos_position_order
    ON videos (position_chapter, position_lesson, created_at, id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_videos_position_format'
          AND conrelid = 'videos'::regclass
    ) THEN
        ALTER TABLE videos
            ADD CONSTRAINT ck_videos_position_format
            CHECK (position IS NULL OR position ~ '^[0-9]+([.][0-9]+)?$');
    END IF;
END
$$;
