package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.core.time.ServerTime;
import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmissionAnswer;
import com.ice.iceenglish.modules.submissions.dto.SubmissionScoreResponse;
import com.ice.iceenglish.modules.submissions.entity.Submission;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dựng các DTO kết quả từ lượt thi và snapshot đề (thuần bộ nhớ, không chạm DB). */
final class ExamResultAssembler {

    static final double POINTS_PER_CORRECT_ANSWER = 0.25;

    private ExamResultAssembler() {
    }

    static OffsetDateTime toVietnamTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(ServerTime.ZONE_ID).toOffsetDateTime();
    }

    static SubmissionScoreResponse toScoreResponse(Submission submission) {
        int totalCorrect = Optional.ofNullable(submission.getTotalCorrect()).orElse(0);
        int totalQuestions = Optional.ofNullable(submission.getTotalQuestions()).orElse(0);
        return SubmissionScoreResponse.builder()
                .submissionId(submission.getId())
                .examId(submission.getExam().getId())
                .examTitle(submission.getExam().getTitle())
                .totalQuestions(totalQuestions)
                .totalCorrect(totalCorrect)
                .correctCount(totalCorrect)
                .totalIncorrect(totalQuestions - totalCorrect)
                .score(totalCorrect * POINTS_PER_CORRECT_ANSWER)
                .startedAt(toVietnamTime(submission.getStartedAt()))
                .submittedAt(toVietnamTime(submission.getSubmittedAt()))
                .build();
    }

    static ExamResultResponse assemble(Submission submission, List<UUID> order, ExamSnapshot exam,
            List<SubmissionAnswer> answers, ResultContent content,
            List<ExamResultResponse.PartTimeLogDto> logs, UUID sampleVideoId, boolean guest) {
        Map<UUID, Integer> indexes = PartOrder.indexes(order);
        List<ExamSnapshot.QuestionSnap> orderedQuestions = new ArrayList<>(exam.questions());
        orderedQuestions.sort(Comparator
                .comparing((ExamSnapshot.QuestionSnap question) ->
                        indexes.getOrDefault(question.partId(), Integer.MAX_VALUE))
                .thenComparing(ExamSnapshot.QuestionSnap::orderIndex));
        Map<UUID, Integer> questionNumbers = new HashMap<>();
        for (int i = 0; i < orderedQuestions.size(); i++) {
            questionNumbers.put(orderedQuestions.get(i).id(), i + 1);
        }
        Map<UUID, SubmissionAnswer> answerByQuestion = new HashMap<>();
        answers.forEach(a -> answerByQuestion.put(a.questionId(), a));
        Map<UUID, String> firstQuestionPartTypes = firstQuestionPartTypes(exam.questions());

        List<ExamResultResponse.QuestionResultDto> details = orderedQuestions.stream()
                .map(question -> {
                    SubmissionAnswer answer = answerByQuestion.getOrDefault(
                            question.id(), new SubmissionAnswer(question.id(), null, false));
                    return ExamResultResponse.QuestionResultDto.builder()
                            .questionId(question.id())
                            .orderIndex(question.orderIndex())
                            .partDisplayIndex(indexes.get(question.partId()))
                            .displayNumber(questionNumbers.get(question.id()))
                            .content(question.content())
                            .partId(question.partId())
                            .optA(question.optA())
                            .optB(question.optB())
                            .optC(question.optC())
                            .optD(question.optD())
                            .selectedOpt(answer.selectedOpt())
                            .correctOpt(question.correctOpt())
                            .isCorrect(answer.isCorrect())
                            .explanation(question.explanation())
                            .partType(firstQuestionPartTypes.get(question.id()))
                            .relatedVideo(relatedVideo(question, answer, content, guest, sampleVideoId))
                            .build();
                })
                .toList();

        int totalCorrect = Optional.ofNullable(submission.getTotalCorrect()).orElse(0);
        int totalQuestions = Optional.ofNullable(submission.getTotalQuestions()).orElse(0);
        return ExamResultResponse.builder()
                .submissionId(submission.getId())
                .examId(exam.examId())
                .examTitle(exam.title())
                .totalQuestions(totalQuestions)
                .totalCorrect(totalCorrect)
                .totalIncorrect(totalQuestions - totalCorrect)
                .score(totalCorrect * POINTS_PER_CORRECT_ANSWER)
                .startedAt(toVietnamTime(submission.getStartedAt()))
                .submittedAt(toVietnamTime(submission.getSubmittedAt()))
                .partTimeLogs(logs)
                .partTranslations(partTranslations(exam, indexes))
                .parts(content != ResultContent.HOMEWORK_REVIEW ? reviewParts(exam, order, indexes) : null)
                .details(details)
                .build();
    }

    private static ExamResultResponse.RelatedVideoDto relatedVideo(ExamSnapshot.QuestionSnap question,
            SubmissionAnswer answer, ResultContent content, boolean guest, UUID sampleVideoId) {
        ExamSnapshot.VideoSnap video = question.video();
        boolean visible = content != ResultContent.HOMEWORK_REVIEW
                && (content == ResultContent.REVIEW || !Boolean.TRUE.equals(answer.isCorrect()))
                && video != null && (!guest || video.id().equals(sampleVideoId));
        if (!visible) {
            return null;
        }
        return ExamResultResponse.RelatedVideoDto.builder()
                .videoId(video.id())
                .videoTitle(video.title())
                .videoUrl(video.url())
                .build();
    }

    private static List<ExamResultResponse.ReviewPartDto> reviewParts(
            ExamSnapshot exam, List<UUID> order, Map<UUID, Integer> indexes) {
        Map<UUID, ExamSnapshot.PartSnap> partsById = new HashMap<>();
        exam.parts().forEach(part -> partsById.put(part.id(), part));
        return order.stream()
                .map(partsById::get)
                .map(part -> ExamResultResponse.ReviewPartDto.builder()
                        .partId(part.id())
                        .partName(part.name())
                        .partType(part.partType().name())
                        .orderIndex(part.orderIndex())
                        .displayIndex(indexes.get(part.id()))
                        .passageText(part.passageText())
                        .build())
                .toList();
    }

    /** Map questionId -> partType, chỉ cho câu đầu tiên của mỗi part READING_8 / READING_10. */
    private static Map<UUID, String> firstQuestionPartTypes(List<ExamSnapshot.QuestionSnap> questions) {
        Map<UUID, ExamSnapshot.QuestionSnap> firstByPart = new HashMap<>();
        for (ExamSnapshot.QuestionSnap question : questions) {
            if (question.partId() == null || !isReadingPassage(question.partType())) {
                continue;
            }
            firstByPart.merge(question.partId(), question, (current, candidate) ->
                    candidate.orderIndex() < current.orderIndex() ? candidate : current);
        }
        Map<UUID, String> result = new HashMap<>();
        firstByPart.values().forEach(question -> result.put(question.id(), question.partType().name()));
        return result;
    }

    private static List<ExamResultResponse.PartTranslationDto> partTranslations(
            ExamSnapshot exam, Map<UUID, Integer> indexes) {
        return exam.parts().stream()
                .filter(part -> isReadingPassage(part.partType())
                        && (part.passageText() != null || part.translate() != null))
                .sorted(Comparator.comparing(part ->
                        indexes.getOrDefault(part.id(), Integer.MAX_VALUE)))
                .map(part -> ExamResultResponse.PartTranslationDto.builder()
                        .partId(part.id())
                        .partName(part.name())
                        .partType(part.partType().name())
                        .passageText(part.passageText())
                        .translate(part.translate())
                        .build())
                .toList();
    }

    private static boolean isReadingPassage(PartType type) {
        return type == PartType.READING_8 || type == PartType.READING_10;
    }
}
