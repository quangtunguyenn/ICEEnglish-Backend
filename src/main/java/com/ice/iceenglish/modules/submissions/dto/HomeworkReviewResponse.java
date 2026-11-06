package com.ice.iceenglish.modules.submissions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record HomeworkReviewResponse(
        UUID submissionId,
        UUID examId,
        String examTitle,
        Integer totalQuestions,
        Integer totalCorrect,
        Integer totalIncorrect,
        Double score,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        List<ExamResultResponse.PartTimeLogDto> partTimeLogs,
        List<QuestionResult> details,
        List<ExamResultResponse.PartTranslationDto> partTranslations) {

    public static HomeworkReviewResponse from(ExamResultResponse result) {
        return new HomeworkReviewResponse(
                result.getSubmissionId(), result.getExamId(), result.getExamTitle(),
                result.getTotalQuestions(), result.getTotalCorrect(), result.getTotalIncorrect(),
                result.getScore(), result.getStartedAt(), result.getSubmittedAt(), result.getPartTimeLogs(),
                result.getDetails().stream().map(QuestionResult::from).toList(), result.getPartTranslations());
    }

    public record QuestionResult(
            UUID questionId,
            Integer orderIndex,
            String content,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID partId,
            String optA,
            String optB,
            String optC,
            String optD,
            String selectedOpt,
            String correctOpt,
            Boolean isCorrect,
            String explanation,
            @JsonProperty("part_type") @JsonInclude(JsonInclude.Include.NON_NULL) String partType) {

        private static QuestionResult from(ExamResultResponse.QuestionResultDto question) {
            return new QuestionResult(question.getQuestionId(), question.getOrderIndex(), question.getContent(),
                    question.getPartId(), question.getOptA(), question.getOptB(), question.getOptC(),
                    question.getOptD(), question.getSelectedOpt(), question.getCorrectOpt(),
                    question.getIsCorrect(), question.getExplanation(), question.getPartType());
        }
    }
}
