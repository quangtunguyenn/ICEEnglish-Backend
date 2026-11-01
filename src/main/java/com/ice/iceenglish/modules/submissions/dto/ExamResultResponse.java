package com.ice.iceenglish.modules.submissions.dto;

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
        private String correctOpt;  // Đáp án đúng
        private Boolean isCorrect;   // true/false
        private String explanation;  // Lời giải chi tiết (Markdown)
        
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