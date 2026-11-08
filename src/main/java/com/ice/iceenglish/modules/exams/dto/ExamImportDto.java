package com.ice.iceenglish.modules.exams.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cấu trúc file JSON giáo viên upload. Mọi kiểm tra nghiệp vụ nằm ở ExamImportValidator
 * (không dùng Bean Validation để thông báo lỗi theo đúng đường dẫn trong file JSON).
 * exam_type có thể lấy từ form upload hoặc metadata JSON.
 */
@Data
public class ExamImportDto {

    @JsonProperty("exam_title")
    private String examTitle;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("video_id")
    private UUID videoId;

    @JsonProperty("answers_url")
    private String answersUrl;

    private ExamMetadata exam;

    private List<PartImport> parts;

    @Data
    public static class ExamMetadata {
        private String title;

        @JsonProperty("exam_type")
        private String examType;

        @JsonProperty("duration_minutes")
        private Integer durationMinutes;

        @JsonProperty("video_id")
        private UUID videoId;

        @JsonProperty("is_answers_revealed")
        private Boolean answersRevealed;

        @JsonProperty("answers_url")
        private String answersUrl;

        @JsonProperty("shuffle_parts")
        private Boolean shuffleParts;
    }

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

        @JsonProperty("fixed_position")
        private Boolean fixedPosition;

        @JsonProperty("shuffle_group")
        private String shuffleGroup;

        private String translate;

        private List<QuestionImport> questions;
    }

    @Data
    public static class QuestionImport {
        private String content;

        @JsonProperty("options")
        private Map<String, String> options;

        @JsonProperty("correct_option")
        @JsonAlias("correct_opt")
        private String correctOption;

        @JsonProperty("related_video_id")
        private UUID relatedVideoId;

        @JsonProperty("opt_a")
        private String optA;

        @JsonProperty("opt_b")
        private String optB;

        @JsonProperty("opt_c")
        private String optC;

        @JsonProperty("opt_d")
        private String optD;

        private String explanation;

        @JsonProperty("order_index")
        private Integer orderIndex;
    }
}
