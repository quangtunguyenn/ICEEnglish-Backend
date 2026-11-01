package com.ice.iceenglish.modules.exams.dto;

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
    private Integer durationMinutes;
    private UUID videoId;
    private boolean answersRevealed;
    private List<ExamPartDto> parts;

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
