package com.ice.iceenglish.modules.submissions.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SubmissionScoreResponse {
    private UUID submissionId;
    private UUID examId;
    private String examTitle;
    private Integer totalQuestions;
    private Integer totalCorrect;
    private Integer totalIncorrect;
    private Double score;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
}
