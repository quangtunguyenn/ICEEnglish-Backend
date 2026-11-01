package com.ice.iceenglish.modules.exams.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Cấu trúc file JSON giáo viên upload. Mọi kiểm tra nghiệp vụ nằm ở ExamImportValidator
 * (không dùng Bean Validation để thông báo lỗi theo đúng đường dẫn trong file JSON).
 * exam_type và video_id không nằm trong file mà lấy từ form upload.
 */
@Data
public class ExamImportDto {

    @JsonProperty("exam_title")
    private String examTitle;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    private List<PartImport> parts;

    @Data
    public static class PartImport {
        @JsonProperty("part_type")
        private String partType;

        @JsonProperty("part_name")
        private String partName;

        @JsonProperty("passage_text")
        private String passageText;

        @JsonProperty("recommended_time_seconds")
        private Integer recommendedTimeSeconds;

        @JsonProperty("order_index")
        private Integer orderIndex;

        private List<QuestionImport> questions;
    }

    @Data
    public static class QuestionImport {
        private String content;

        /** Phải có đủ 4 khóa A, B, C, D. */
        private Map<String, String> options;

        @JsonProperty("correct_option")
        private String correctOption;

        private String explanation;

        @JsonProperty("order_index")
        private Integer orderIndex;
    }
}
