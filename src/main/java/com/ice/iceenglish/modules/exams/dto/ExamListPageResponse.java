package com.ice.iceenglish.modules.exams.dto;

import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ExamListPageResponse {
    private List<Item> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    @Data
    @Builder
    public static class Item {
        private UUID id;
        private String title;
        private ExamType mode;
        private ExamStatus status;
        private Integer durationMinutes;
        private long totalQuestions;
        private OffsetDateTime createdAt;
        private Boolean attempted;
        private Double latestScore;
    }
}
