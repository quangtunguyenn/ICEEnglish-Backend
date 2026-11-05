-- Index hiệu năng cho các truy vấn nóng (nộp bài, xem kết quả, tải đề, bài về nhà, từ vựng).
-- Tên trùng với @Table(indexes = ...) trong entity; IF NOT EXISTS nên chạy an toàn dù Hibernate đã tạo trước.
CREATE INDEX IF NOT EXISTS idx_submissions_user_exam_mode ON submissions (user_id, exam_id, mode, started_at);
CREATE INDEX IF NOT EXISTS idx_submissions_user_mode_submitted ON submissions (user_id, mode, submitted_at);
CREATE INDEX IF NOT EXISTS idx_sub_details_submission ON sub_details (submission_id);
CREATE INDEX IF NOT EXISTS idx_questions_part ON questions (part_id);
CREATE INDEX IF NOT EXISTS idx_questions_exam ON questions (exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_parts_exam ON exam_parts (exam_id);
CREATE INDEX IF NOT EXISTS idx_exams_video_type ON exams (video_id, exam_type);
CREATE INDEX IF NOT EXISTS idx_exams_status_created ON exams (status, created_at);
CREATE INDEX IF NOT EXISTS idx_vocabularies_video ON vocabularies (video_id);
