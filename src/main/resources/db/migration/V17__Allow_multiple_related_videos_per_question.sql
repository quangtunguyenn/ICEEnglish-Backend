CREATE TABLE IF NOT EXISTS question_related_videos (
    question_id uuid NOT NULL,
    video_id uuid NOT NULL,
    order_index integer NOT NULL,
    PRIMARY KEY (question_id, order_index)
);

DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.question_related_videos'::regclass
          AND contype = 'f'
    LOOP
        EXECUTE format('ALTER TABLE public.question_related_videos DROP CONSTRAINT %I', fk.conname);
    END LOOP;
END $$;

DELETE FROM question_related_videos duplicate
USING question_related_videos earlier
WHERE duplicate.question_id = earlier.question_id
  AND duplicate.video_id = earlier.video_id
  AND duplicate.order_index > earlier.order_index;

INSERT INTO question_related_videos (question_id, video_id, order_index)
SELECT q.id, q.related_video_id,
       COALESCE(MAX(existing.order_index) + 1, 0)
FROM questions q
LEFT JOIN question_related_videos existing ON existing.question_id = q.id
WHERE q.related_video_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM question_related_videos linked
      WHERE linked.question_id = q.id
        AND linked.video_id = q.related_video_id
  )
GROUP BY q.id, q.related_video_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_question_related_videos_question_video
    ON question_related_videos(question_id, video_id);
CREATE INDEX IF NOT EXISTS idx_question_related_videos_video
    ON question_related_videos(video_id);

ALTER TABLE question_related_videos
    ADD CONSTRAINT fk_question_related_videos_question
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE;
ALTER TABLE question_related_videos
    ADD CONSTRAINT fk_question_related_videos_video
    FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE;

ALTER TABLE questions DROP COLUMN IF EXISTS related_video_id;
