package com.ice.iceenglish.modules.exams.enums;

/**
 * DRAFT     : vừa import, chỉ giáo viên/admin xem được (để preview và duyệt).
 * PUBLISHED : học sinh làm bài được.
 * ARCHIVED  : ngừng sử dụng nhưng vẫn giữ lịch sử làm bài (thay cho xóa cứng).
 */
public enum ExamStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
