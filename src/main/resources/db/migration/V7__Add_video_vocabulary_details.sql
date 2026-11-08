-- Apply before deploying code that reads the new columns.
ALTER TABLE vocabularies
    ADD COLUMN IF NOT EXISTS phonetic varchar(250),
    ADD COLUMN IF NOT EXISTS example_sentence text,
    ADD COLUMN IF NOT EXISTS example_sentence_translate text;
