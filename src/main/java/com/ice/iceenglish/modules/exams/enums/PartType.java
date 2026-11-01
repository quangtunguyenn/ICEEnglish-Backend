package com.ice.iceenglish.modules.exams.enums;

/**
 * Danh sách loại phần thi mà Frontend biết cách render.
 * Thêm loại mới ở đây (và ở Frontend) trước khi giáo viên có thể upload.
 */
public enum PartType {
    CLOZE_TEST(true),
    ARRANGEMENT(false),
    READING(true),
    GRAMMAR(false);

    private final boolean passageRequired;

    PartType(boolean passageRequired) {
        this.passageRequired = passageRequired;
    }

    public boolean isPassageRequired() {
        return passageRequired;
    }

    /** Trả về null nếu giá trị không hợp lệ (để validator tự gom lỗi). */
    public static PartType parse(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        for (PartType t : values()) {
            if (t.name().equalsIgnoreCase(v)) {
                return t;
            }
        }
        return null;
    }
}
