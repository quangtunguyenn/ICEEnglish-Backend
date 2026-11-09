-- Dữ liệu seed cho load test (chạy trên DB iceenglish_loadtest, KHÔNG chạy trên DB thật).
DO $$
DECLARE
    pw text := crypt('pass1234', gen_salt('bf', 10));
    vid uuid;
    ex uuid;
    pt uuid;
    vids uuid[] := '{}';
    i int;
    q int;
    qn int;
    ptype text;
    ptypes text[] := ARRAY['GRAMMAR','CLOZE_TEST','READING_8','READING_10'];
BEGIN
    INSERT INTO users (id, username, password_hash, role, full_name, created_at)
    SELECT gen_random_uuid(), 's' || lpad(g::text, 4, '0'), pw, 'STUDENT', 'Student ' || g, now()
    FROM generate_series(1, 1000) g;

    INSERT INTO users (id, username, password_hash, role, full_name, created_at)
    VALUES (gen_random_uuid(), 'admin', pw, 'ADMIN', 'Admin', now());

    FOR i IN 1..50 LOOP
        -- Video 1 có id cố định để cấu hình chế độ khách (app.guest.video-id).
        vid := CASE i WHEN 1 THEN '11111111-1111-1111-1111-111111111111'::uuid ELSE gen_random_uuid() END;
        vids := vids || vid;
        INSERT INTO videos (id, title, video_url, document_url, created_at)
        VALUES (vid, 'Chuyen de ' || i, 'https://youtu.be/x' || i, 'https://drive.google.com/d' || i, now());
        INSERT INTO vocabularies (id, video_id, word, meaning, created_at)
        SELECT gen_random_uuid(), vid, 'word' || i || '_' || g, 'nghia ' || g, now() + (g || ' ms')::interval
        FROM generate_series(1, 14) g;

        ex := gen_random_uuid();
        INSERT INTO exams (id, title, exam_type, status, is_answers_revealed, shuffle_parts, answers_url, video_id, created_at)
        VALUES (ex, 'Homework ' || i, 'HOMEWORK', 'PUBLISHED', false, false, 'https://drive.google.com/a' || i, vid, now());
        INSERT INTO questions (id, exam_id, content, opt_a, opt_b, opt_c, opt_d, correct_opt, explanation,
                               related_video_id, order_index, created_at)
        SELECT gen_random_uuid(), ex, repeat('Homework question text ', 5) || g, 'optA', 'optB', 'optC', 'optD',
               (ARRAY['A','B','C','D'])[1 + (g % 4)], repeat('Giai thich chi tiet ', 15), vid, g, now()
        FROM generate_series(1, 12) g;
    END LOOP;

    -- Hai đề 100 câu (MOCK và GENERAL_EXAM), 4 part x 25 câu, đoạn văn dài như đề thật.
    -- Id cố định để cấu hình đề mẫu cho chế độ khách (app.guest.exam-ids).
    FOR i IN 1..2 LOOP
        ex := CASE i WHEN 1 THEN '22222222-2222-2222-2222-222222222222'::uuid
                     ELSE '33333333-3333-3333-3333-333333333333'::uuid END;
        INSERT INTO exams (id, title, exam_type, status, duration_minutes, is_answers_revealed, shuffle_parts, answers_url, created_at)
        VALUES (ex, CASE i WHEN 1 THEN 'Mock Exam 100' ELSE 'General Exam 100' END,
                CASE i WHEN 1 THEN 'MOCK' ELSE 'GENERAL_EXAM' END, 'PUBLISHED',
                CASE i WHEN 1 THEN 60 ELSE NULL END, false, true, 'https://drive.google.com/exam' || i, now());
        qn := 0;
        FOR q IN 1..4 LOOP
            ptype := ptypes[q];
            pt := gen_random_uuid();
            INSERT INTO exam_parts (id, exam_id, part_type, part_name, passage_text, translate, order_index, fixed_position, created_at)
            VALUES (pt, ex, ptype, 'Part ' || q,
                    CASE WHEN ptype = 'GRAMMAR' THEN NULL ELSE repeat('This is a long reading passage sentence. ', 80) END,
                    CASE WHEN ptype IN ('READING_8','READING_10') THEN repeat('Day la doan dich tieng Viet. ', 100) END,
                    q, false, now());
            INSERT INTO questions (id, part_id, content, opt_a, opt_b, opt_c, opt_d, correct_opt, explanation,
                                   related_video_id, order_index, created_at)
            SELECT gen_random_uuid(), pt, repeat('Question text content ', 6) || (qn + g), 'option A text', 'option B text',
                   'option C text', 'option D text', (ARRAY['A','B','C','D'])[1 + ((qn + g) % 4)],
                   repeat('Loi giai chi tiet cho cau hoi nay. ', 10),
                   CASE WHEN g % 3 = 0 THEN vids[1 + ((qn + g) % 50)] END, qn + g, now()
            FROM generate_series(1, 25) g;
            qn := qn + 25;
        END LOOP;
    END LOOP;
END $$;

ANALYZE;
SELECT 'users', count(*) FROM users UNION ALL SELECT 'videos', count(*) FROM videos
UNION ALL SELECT 'exams', count(*) FROM exams UNION ALL SELECT 'exam_parts', count(*) FROM exam_parts
UNION ALL SELECT 'questions', count(*) FROM questions UNION ALL SELECT 'vocabularies', count(*) FROM vocabularies;
