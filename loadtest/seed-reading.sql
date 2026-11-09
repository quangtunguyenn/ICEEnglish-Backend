-- Dữ liệu bài đọc cho load test (chạy trên DB iceenglish_loadtest, sau khi đã tạo bảng bằng V6__Create_reading_library.sql).
-- 20 bài PUBLISHED thuộc 4 chủ đề; mỗi bài 12 đoạn văn, 10 câu hỏi (4 đáp án, đáp án đúng là A), 10 từ vựng.
-- Bài có số thứ tự chẵn mở cho khách (guest_accessible).
DO $$
DECLARE
    topics uuid[] := '{}';
    tid uuid;
    bid uuid;
    qid uuid;
    i int;
    q int;
    blocks jsonb;
BEGIN
    IF EXISTS (SELECT 1 FROM reading_blogs) THEN
        RAISE NOTICE 'reading data already seeded';
        RETURN;
    END IF;

    FOR i IN 1..4 LOOP
        tid := gen_random_uuid();
        topics := topics || tid;
        INSERT INTO reading_topics (id, slug, name) VALUES (tid, 'topic-' || i, 'Topic ' || i);
    END LOOP;

    FOR i IN 1..20 LOOP
        bid := gen_random_uuid();
        SELECT jsonb_agg(jsonb_build_object(
                   'id', 'p' || g,
                   'text', repeat('This is a long paragraph of the reading passage for load testing. ', 6),
                   'translation', repeat('Day la doan dich tieng Viet cua bai doc. ', 6)))
          INTO blocks FROM generate_series(1, 12) g;
        INSERT INTO reading_blogs (id, topic_id, title, summary, content_type, difficulty,
                                   estimated_reading_minutes, passage_blocks, guest_accessible, status,
                                   created_by, published_at)
        VALUES (bid, topics[1 + (i % 4)], 'Reading blog ' || i, repeat('Summary text ', 12),
                (ARRAY['ARTICLE','MAGAZINE','STORY','PASSAGE'])[1 + (i % 4)],
                (ARRAY['EASY','MEDIUM','HARD'])[1 + (i % 3)], 8, blocks, i % 2 = 0, 'PUBLISHED',
                'admin', now() - (i || ' minutes')::interval);

        FOR q IN 1..10 LOOP
            qid := gen_random_uuid();
            INSERT INTO reading_questions (id, blog_id, order_index, content, skill_type, evidence_block_ids, explanation)
            VALUES (qid, bid, q, 'Question ' || q || ' ' || repeat('about the passage ', 4),
                    (ARRAY['SKIMMING','SCANNING','INFERENCE','VOCABULARY_IN_CONTEXT','REFERENCE'])[1 + (q % 5)],
                    '["p1","p2"]'::jsonb, repeat('Detailed explanation. ', 10));
            INSERT INTO reading_answers (id, question_id, option_key, content, is_correct, explanation)
            SELECT gen_random_uuid(), qid, k, 'Answer option ' || k, k = 'A', 'Option explanation ' || k
            FROM unnest(ARRAY['A','B','C','D']) AS k;
        END LOOP;

        INSERT INTO reading_vocabularies (id, blog_id, order_index, word, phonetic, meaning,
                                          example_sentence, example_translation, passage_block_id)
        SELECT gen_random_uuid(), bid, g, 'word' || i || '_' || g, '/word/', 'nghia ' || g,
               'Example sentence ' || g, 'Cau vi du ' || g, 'p' || g
        FROM generate_series(1, 10) g;
    END LOOP;
END $$;

ANALYZE;
SELECT 'reading_blogs', count(*) FROM reading_blogs UNION ALL SELECT 'reading_questions', count(*) FROM reading_questions;
