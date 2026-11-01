package com.ice.iceenglish.modules.exams.dto;

import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ExamImportResponseDto {
    private UUID id;
    private String title;
    private ExamStatus status;
    private int totalParts;
    private int totalQuestions;
    /** Cảnh báo không chặn việc import, giáo viên nên xem lại ở bước preview. */
    private List<String> warnings;
}
