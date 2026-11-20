ALTER TABLE favorite_vocabularies
    ADD COLUMN IF NOT EXISTS word VARCHAR(100),
    ADD COLUMN IF NOT EXISTS meaning TEXT;

UPDATE favorite_vocabularies favorite
SET word = vocabulary.word,
    meaning = vocabulary.meaning
FROM vocabularies vocabulary
WHERE favorite.vocabulary_id = vocabulary.id
  AND (favorite.word IS NULL OR favorite.meaning IS NULL);

DELETE FROM favorite_vocabularies older
USING favorite_vocabularies newer
WHERE older.user_id = newer.user_id
  AND lower(btrim(older.word)) = lower(btrim(newer.word))
  AND lower(btrim(older.meaning)) = lower(btrim(newer.meaning))
  AND (older.created_at > newer.created_at
       OR (older.created_at = newer.created_at AND older.id > newer.id));

ALTER TABLE favorite_vocabularies
    ALTER COLUMN word SET NOT NULL,
    ALTER COLUMN meaning SET NOT NULL,
    DROP CONSTRAINT IF EXISTS uk_favorite_vocabularies_user_vocabulary,
    DROP CONSTRAINT IF EXISTS fk_favorite_vocabularies_vocabulary,
    DROP COLUMN IF EXISTS vocabulary_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_vocabularies_user_word_meaning
    ON favorite_vocabularies(user_id, lower(btrim(word)), lower(btrim(meaning)));
