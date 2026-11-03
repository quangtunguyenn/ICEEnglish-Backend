package com.ice.iceenglish.modules.exams.enums;

/**
 * Danh sách loại phần thi mà Frontend biết cách render.
 * Thêm loại mới ở đây (và ở Frontend) trước khi giáo viên có thể upload.
 */
public enum PartType {
    GRAMMAR(false, 0),
    CLOZE_TEST(true, 4 * 60),
    ARRANGEMENT(false, 5 * 60),
    READING_8(true, 10 * 60),
    READING_10(true, 15 * 60),
    FILLIN_CLOZE(true, 7 * 60);

    private final boolean passageRequired;
    private final int standardTimeSeconds;

    PartType(boolean passageRequired, int standardTimeSeconds) {
        this.passageRequired = passageRequired;
        this.standardTimeSeconds = standardTimeSeconds;
    }

    public boolean isPassageRequired() {
        return passageRequired;
    }

    public int getStandardTimeSeconds() {
        return standardTimeSeconds;
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
