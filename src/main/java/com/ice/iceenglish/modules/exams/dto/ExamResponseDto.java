package com.ice.iceenglish.modules.exams.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu đề thi cho học sinh làm bài.
 * KHÔNG chứa correctOpt và explanation để không lộ đáp án.
 */
@Data
@Builder
public class ExamResponseDto {
    private UUID id;
    private String title;
    private ExamType examType;
    private UUID videoId;
    private Integer durationMinutes;
    private boolean answersRevealed;
    @JsonProperty("answers_url")
    private String answersUrl;
    private List<ExamPartDto> parts;

    /** Câu hỏi của HOMEWORK (không có part). Rỗng với đề thường. */
    private List<QuestionDto> questions;

    @Data
    @Builder
    public static class ExamPartDto {
        private UUID id;
        private String partName;
        private PartType partType;
        private String passageText; // Frontend hiển thị bên TRÁI màn hình
        private Integer recommendedTimeSeconds;
        private Integer orderIndex;
        private List<QuestionDto> questions; // Frontend hiển thị bên PHẢI
    }

    @Data
    @Builder
    public static class QuestionDto {
        private UUID id;
        private Integer orderIndex;
        private String content;
        private String optA;
        private String optB;
        private String optC;
        private String optD;
    }
}
