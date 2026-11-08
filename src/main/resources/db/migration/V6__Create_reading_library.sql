-- Apply explicitly using the project's database deployment process.
-- This migration only creates objects belonging to the reading library.
CREATE TABLE reading_topics (
    id uuid PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE,
    name varchar(150) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE reading_blogs (
    id uuid PRIMARY KEY,
    topic_id uuid NOT NULL REFERENCES reading_topics(id),
    title varchar(250) NOT NULL,
    summary text NOT NULL,
    content_type varchar(20) NOT NULL CHECK (content_type IN ('ARTICLE', 'MAGAZINE', 'STORY', 'PASSAGE')),
    difficulty varchar(10) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    estimated_reading_minutes integer CHECK (estimated_reading_minutes > 0),
    passage_blocks jsonb NOT NULL,
    cover_image_url varchar(1000),
    source_name varchar(250),
    source_url varchar(1000),
    guest_accessible boolean NOT NULL DEFAULT false,
    status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    revision integer NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_by varchar(100) NOT NULL,
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_reading_blogs_status_published ON reading_blogs(status, published_at DESC, id);
CREATE INDEX idx_reading_blogs_topic_status_published ON reading_blogs(topic_id, status, published_at DESC, id);

CREATE TABLE reading_questions (
    id uuid PRIMARY KEY,
    blog_id uuid NOT NULL REFERENCES reading_blogs(id) ON DELETE CASCADE,
    order_index integer NOT NULL CHECK (order_index > 0),
    content text NOT NULL,
    skill_type varchar(32) NOT NULL CHECK (skill_type IN ('SKIMMING', 'SCANNING', 'INFERENCE', 'VOCABULARY_IN_CONTEXT', 'REFERENCE')),
    evidence_block_ids jsonb NOT NULL,
    explanation text NOT NULL,
    UNIQUE (blog_id, order_index)
);

CREATE TABLE reading_answers (
    id uuid PRIMARY KEY,
    question_id uuid NOT NULL REFERENCES reading_questions(id) ON DELETE CASCADE,
    option_key char(1) NOT NULL CHECK (option_key IN ('A', 'B', 'C', 'D')),
    content text NOT NULL,
    is_correct boolean NOT NULL,
    explanation text,
    UNIQUE (question_id, option_key)
);
CREATE UNIQUE INDEX idx_reading_answers_one_correct ON reading_answers(question_id) WHERE is_correct;

CREATE TABLE reading_vocabularies (
    id uuid PRIMARY KEY,
    blog_id uuid NOT NULL REFERENCES reading_blogs(id) ON DELETE CASCADE,
    order_index integer NOT NULL CHECK (order_index > 0),
    word varchar(150) NOT NULL,
    phonetic varchar(250),
    meaning text NOT NULL,
    example_sentence text,
    example_translation text,
    passage_block_id varchar(80),
    UNIQUE (blog_id, order_index)
);
