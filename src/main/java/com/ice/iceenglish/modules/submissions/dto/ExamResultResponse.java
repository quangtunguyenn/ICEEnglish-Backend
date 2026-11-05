package com.ice.iceenglish.modules.submissions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
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
    private LocalDateTime submittedAt;
    private List<PartTimeLogDto> partTimeLogs;
    private List<QuestionResultDto> details;

    // Bản dịch đoạn văn của các phần READING_8 / READING_10
    private List<PartTranslationDto> partTranslations;

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
        private String selectedOpt; // Đáp án học sinh chọn ('A', 'B', 'C', 'D' hoặc null)
        private String correctOpt; // Đáp án đúng
        private Boolean isCorrect; // true/false
        private String explanation; // Lời giải chi tiết (Markdown)

        // Chỉ có ở câu đầu tiên của part READING_8 / READING_10 để frontend render phần dịch
        @JsonProperty("part_type")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String partType;

        // Video gợi ý ôn tập nếu học sinh làm SAI
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