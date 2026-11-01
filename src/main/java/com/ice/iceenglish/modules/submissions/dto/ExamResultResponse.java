package com.ice.iceenglish.modules.submissions.dto;

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
    private Double score; // Điểm số (thang điểm 10)
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private List<QuestionResultDto> details;

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
        private String videoUrl;
    }
}