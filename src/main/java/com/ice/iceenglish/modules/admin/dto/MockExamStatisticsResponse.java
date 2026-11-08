package com.ice.iceenglish.modules.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MockExamStatisticsResponse {

    private MockExamStatisticsResponse() {
    }

    public record ExamPage(
            List<ExamItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record ExamItem(
            UUID examId,
            String title,
            Integer durationMinutes,
            int totalQuestions,
            OffsetDateTime publishedAt,
            long submittedCount,
            Double averageScore) {
    }

    public record Overview(
            UUID examId,
            String title,
            int totalQuestions,
            long submittedCount,
            Double averageScore,
            Double highestScore,
            Double lowestScore,
            Double medianScore,
            Long averageDurationSeconds,
            List<ScoreBucket> scoreDistribution) {
    }

    public record ScoreBucket(
            String label,
            double min,
            double max,
            long count,
            double percent) {
    }

    public record QuestionStats(
            UUID examId,
            long submittedCount,
            List<QuestionItem> questions) {
    }

    public record QuestionItem(
            UUID questionId,
            UUID partId,
            String partName,
            int questionNumber,
            String content,
            String correctOpt,
            long answeredCount,
            long unansweredCount,
            long correctCount,
            long wrongCount,
            Double correctPercent,
            Double wrongPercent,
            List<OptionStat> optionStats) {
    }

    public record OptionStat(
            String option,
            long count,
            Double percent,
            boolean correct) {
    }

    public record Top(
            UUID examId,
            int limit,
            List<TopItem> items) {
    }

    public record TopItem(
            int rank,
            UUID userId,
            String fullName,
            UUID submissionId,
            double score,
            int correctCount,
            int totalQuestions,
            long durationSeconds,
            OffsetDateTime submittedAt) {
    }
}
