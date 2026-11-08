package com.ice.iceenglish.modules.submissions.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
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
    private OffsetDateTime serverNow;
    private OffsetDateTime startedAt;
    private OffsetDateTime expiresAt;
    private List<PartTimeDto> parts;
    private List<UUID> partOrder;

    @Data
    @Builder
    public static class PartTimeDto {
        private UUID partId;
        private String partName;
        private Integer orderIndex;
        private Integer displayIndex;
        private Integer recommendedTimeSeconds;
    }
}
