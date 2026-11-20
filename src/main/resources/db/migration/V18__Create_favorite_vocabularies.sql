CREATE TABLE IF NOT EXISTS favorite_vocabularies (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    vocabulary_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_favorite_vocabularies_user_vocabulary UNIQUE (user_id, vocabulary_id)
);

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'favorite_vocabularies'::regclass
          AND contype = 'f'
    LOOP
        EXECUTE format('ALTER TABLE favorite_vocabularies DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    ALTER TABLE favorite_vocabularies
        ADD CONSTRAINT fk_favorite_vocabularies_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    ALTER TABLE favorite_vocabularies
        ADD CONSTRAINT fk_favorite_vocabularies_vocabulary
        FOREIGN KEY (vocabulary_id) REFERENCES vocabularies(id) ON DELETE CASCADE;
END $$;

CREATE INDEX IF NOT EXISTS idx_favorite_vocabularies_user
    ON favorite_vocabularies(user_id);
