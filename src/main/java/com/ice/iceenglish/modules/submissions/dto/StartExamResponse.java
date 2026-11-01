package com.ice.iceenglish.modules.submissions.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Data
@Builder
public class StartExamResponse {
    private UUID submissionId;
    private UUID examId;
    private String mode;
    private Integer totalQuestions;
    private Integer timeLimitMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private List<PartTimeDto> parts;

    @Data
    @Builder
    public static class PartTimeDto {
        private UUID partId;
        private String partName;
        private Integer orderIndex;
    }
}
