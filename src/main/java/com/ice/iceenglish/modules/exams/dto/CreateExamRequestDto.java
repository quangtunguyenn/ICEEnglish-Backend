package com.ice.iceenglish.modules.exams.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateExamRequestDto {
    private ExamInfo exam;
    private List<PartInfo> parts;

    @Data
    public static class ExamInfo {
        private String title;
        private String examType;
        private Integer durationMinutes;
        private Boolean isAnswersRevealed;
        private UUID videoId; // Có thể null
    }

    @Data
    public static class PartInfo {
        private String partName;
        private String partType;
        private String passageText;
        private Integer recommendedTimeSeconds;
        private Integer orderIndex;
        private List<QuestionInfo> questions;
    }

    @Data
    public static class QuestionInfo {
        private Integer orderIndex;
        private String content;
        private String optA;
        private String optB;
        private String optC;
        private String optD;
        private String correctOpt;
        private String explanation;
        private UUID relatedVideoId;
    }
}