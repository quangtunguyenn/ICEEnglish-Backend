package com.ice.iceenglish.modules.submissions.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PracticeHistoryResponse {
    private UUID examId;
    private String examTitle;
    private List<Item> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    @Data
    @Builder
    public static class Item {
        private UUID submissionId;
        private OffsetDateTime submittedAt;
        private Double score;
        private Integer totalQuestions;
        private Integer totalCorrect;
    }
}
