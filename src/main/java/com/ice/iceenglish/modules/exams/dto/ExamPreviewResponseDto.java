package com.ice.iceenglish.modules.exams.dto;

import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Dữ liệu đề thi cho giáo viên/admin duyệt: có đáp án đúng và lời giải thích. Không bao giờ trả cho học sinh. */
@Data
@Builder
public class ExamPreviewResponseDto {
    private UUID id;
    private String title;
    private ExamType examType;
    private UUID videoId;
    private ExamStatus status;
    private Integer durationMinutes;
    private boolean answersRevealed;
    private boolean shuffleParts;
    private String answersUrl;
    private String createdBy;
    private List<PartDto> parts;

    @Data
    @Builder
    public static class PartDto {
        private UUID id;
        private String partName;
        private PartType partType;
        private String passageText;
        private String translate;
        private Integer recommendedTimeSeconds;
        private Integer orderIndex;
        private boolean fixedPosition;
        private String shuffleGroup;
        private List<QuestionDto> questions;
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
        private String correctOpt;
        private String explanation;
        private UUID relatedVideoId;
    }
}
