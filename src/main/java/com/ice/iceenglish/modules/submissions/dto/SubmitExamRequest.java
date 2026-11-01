package com.ice.iceenglish.modules.submissions.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class SubmitExamRequest {
    private UUID submissionId; // Lượt thi đã tạo lúc bấm "Bắt đầu"
    private List<UserAnswerDto> answers;
    private List<PartTimeLogDto> partLogs;

    @Data
    public static class UserAnswerDto {
        private UUID questionId;
        private String selectedOpt; // 'A', 'B', 'C', 'D' hoặc null nếu bỏ trống
    }

    @Data
    public static class PartTimeLogDto {
        private UUID partId;
        private Integer timeSpentSeconds;
    }
}