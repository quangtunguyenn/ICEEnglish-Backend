package com.ice.iceenglish.modules.submissions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ExamResultResponse {
    private UUID submissionId;
    private UUID examId;
    private String examTitle;
    private Integer totalQuestions;
    private Integer totalCorrect;
    private Integer totalIncorrect;
    private Double score; // Điểm số: mỗi câu đúng được 0.25 điểm
    private LocalDateTime startedAt;
    private OffsetDateTime submittedAt;
    private List<PartTimeLogDto> partTimeLogs;
    private List<QuestionResultDto> details;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ReviewPartDto> parts;

    // Bản dịch đoạn văn của các phần READING_8 / READING_10
    private List<PartTranslationDto> partTranslations;

    @Data
    @Builder
    public static class ReviewPartDto {
        private UUID partId;
        private String partName;
        private String partType;
        private Integer orderIndex;
        private String passageText;
    }

    @Data
    @Builder
    public static class PartTranslationDto {
        private UUID partId;
        private String partName;
        private String partType;
        @JsonProperty("passage_text")
        private String passageText;
        private String translate;
    }

    @Data
    @Builder
    public static class PartTimeLogDto {
        private UUID partId;
        private String partName;
        private Integer timeSpentSeconds;
    }

    @Data
    @Builder
    public static class QuestionResultDto {
        private UUID questionId;
        private Integer orderIndex;
        private String content;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private UUID partId;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String optA;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String optB;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String optC;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String optD;
        private String selectedOpt; // Đáp án học sinh chọn ('A', 'B', 'C', 'D' hoặc null)
        private String correctOpt; // Đáp án đúng
        private Boolean isCorrect; // true/false
        private String explanation; // Lời giải chi tiết (Markdown)

        // Chỉ có ở câu đầu tiên của part READING_8 / READING_10 để frontend render phần dịch
        @JsonProperty("part_type")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String partType;

        // Review luyện đề và thi thử trả video cho mọi câu có liên kết.
        private RelatedVideoDto relatedVideo;
    }

    @Data
    @Builder
    public static class RelatedVideoDto {
        private UUID videoId;
        private String videoTitle;
        @JsonProperty("video_url")
        private String videoUrl;
    }
}