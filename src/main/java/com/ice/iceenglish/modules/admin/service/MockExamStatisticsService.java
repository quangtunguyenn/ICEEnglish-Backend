package com.ice.iceenglish.modules.admin.service;

import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.ExamItem;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.ExamPage;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.OptionStat;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.Overview;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.QuestionItem;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.QuestionStats;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.ScoreBucket;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.Top;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.TopItem;
import com.ice.iceenglish.modules.admin.exception.MockExamStatisticsException;
import com.ice.iceenglish.core.time.ServerTime;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository.MockTopData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockExamStatisticsService {

    private static final List<Double> DEFAULT_BUCKETS = List.of(0d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d);
    private static final List<String> OPTIONS = List.of("A", "B", "C", "D");

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubDetailRepository subDetailRepository;

    public ExamPage listPublishedExams(int page, int size, String keyword) {
        validatePage(page, size);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<Exam> exams = examRepository.findByExamTypeAndStatusAndTitleContainingIgnoreCase(
                ExamType.MOCK,
                ExamStatus.PUBLISHED,
                normalizedKeyword,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));

        List<UUID> examIds = exams.getContent().stream().map(Exam::getId).toList();
        Map<UUID, List<MockTopData>> submissionsByExam = new HashMap<>();
        if (!examIds.isEmpty()) {
            bestSubmissions(submissionRepository.findMockTopDataByExamIds(examIds)).forEach(submission ->
                    submissionsByExam.computeIfAbsent(submission.getExamId(), ignored -> new ArrayList<>())
                            .add(submission));
        }

        Map<UUID, Integer> questionCounts = new HashMap<>();
        if (!examIds.isEmpty()) {
            questionRepository.countQuestionsByExamIds(examIds).forEach(count ->
                    questionCounts.put(count.getExamId(), count.getTotalQuestions().intValue()));
        }

        List<ExamItem> items = exams.getContent().stream()
                .map(exam -> new ExamItem(
                        exam.getId(),
                        exam.getTitle(),
                        exam.getDurationMinutes(),
                        questionCounts.getOrDefault(exam.getId(), 0),
                        toVietnamTime(exam.getCreatedAt()),
                        submissionsByExam.getOrDefault(exam.getId(), List.of()).size(),
                        average(submissionsByExam.getOrDefault(exam.getId(), List.of()).stream()
                                .map(MockExamStatisticsService::scoreFor)
                                .toList())))
                .toList();

        return new ExamPage(items, page, size, exams.getTotalElements(), exams.getTotalPages());
    }

    public Overview overview(UUID examId, String bucketParameter) {
        Exam exam = requireMockExam(examId);
        List<MockTopData> submissions = bestSubmissions(submissionRepository.findMockTopDataByExamId(examId));
        List<Double> scores = submissions.stream()
                .map(MockExamStatisticsService::scoreFor)
                .filter(score -> score != null)
                .sorted()
                .toList();

        Double averageScore = scores.isEmpty() ? null
                : roundScore(scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
        Double highestScore = scores.isEmpty() ? null : scores.get(scores.size() - 1);
        Double lowestScore = scores.isEmpty() ? null : scores.get(0);
        Double medianScore = median(scores);

        OptionalDouble averageDurationValue = submissions.stream()
                .map(MockExamStatisticsService::durationSeconds)
                .filter(duration -> duration != null)
                .mapToLong(Long::longValue)
                .average();
        Long averageDuration = averageDurationValue.isPresent()
                ? Math.round(averageDurationValue.getAsDouble())
                : null;

        int totalQuestions = Math.toIntExact(questionRepository.countByExamId(examId));
        return new Overview(
                exam.getId(),
                exam.getTitle(),
                totalQuestions,
                submissions.size(),
                averageScore,
                highestScore,
                lowestScore,
                medianScore,
                averageDuration,
                distribution(scores, bucketParameter));
    }

    public QuestionStats questions(UUID examId) {
        requireMockExam(examId);
        List<MockTopData> submissions = bestSubmissions(submissionRepository.findMockTopDataByExamId(examId));
        List<Question> questions = new ArrayList<>(questionRepository.findAllWithDetailsByExamId(examId));
        questions.sort(Comparator
                .comparingInt((Question question) -> partOrder(question.getPart()))
                .thenComparingInt(question -> question.getOrderIndex() == null ? 0 : question.getOrderIndex()));

        Map<UUID, Map<String, Long>> optionCounts = new HashMap<>();
        Map<UUID, Long> answeredCounts = new HashMap<>();
        Map<UUID, Long> correctCounts = new HashMap<>();
        List<UUID> submissionIds = submissions.stream().map(MockTopData::getSubmissionId).toList();
        if (!submissionIds.isEmpty()) {
            subDetailRepository.findMockQuestionAnswerDataBySubmissionIds(submissionIds).forEach(answer -> {
                if (answer.getSelectedOpt() == null || answer.getSelectedOpt().isBlank()) {
                    return;
                }
                UUID questionId = answer.getQuestionId();
                answeredCounts.merge(questionId, 1L, Long::sum);
                if (Boolean.TRUE.equals(answer.getCorrect())) {
                    correctCounts.merge(questionId, 1L, Long::sum);
                }
                String option = answer.getSelectedOpt().trim().toUpperCase();
                if (OPTIONS.contains(option)) {
                    optionCounts.computeIfAbsent(questionId, ignored -> new HashMap<>())
                            .merge(option, 1L, Long::sum);
                }
            });
        }

        List<QuestionItem> items = new ArrayList<>(questions.size());
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            long answered = answeredCounts.getOrDefault(question.getId(), 0L);
            long correct = correctCounts.getOrDefault(question.getId(), 0L);
            long wrong = answered - correct;
            Map<String, Long> counts = optionCounts.getOrDefault(question.getId(), Map.of());
            List<OptionStat> options = OPTIONS.stream()
                    .map(option -> new OptionStat(
                            option,
                            counts.getOrDefault(option, 0L),
                            percent(counts.getOrDefault(option, 0L), answered),
                            option.equalsIgnoreCase(question.getCorrectOpt())))
                    .toList();
            ExamPart part = question.getPart();
            items.add(new QuestionItem(
                    question.getId(),
                    part == null ? null : part.getId(),
                    part == null ? null : part.getPartName(),
                    index + 1,
                    question.getContent(),
                    question.getCorrectOpt(),
                    answered,
                    Math.max(0L, submissions.size() - answered),
                    correct,
                    wrong,
                    percent(correct, answered),
                    percent(wrong, answered),
                    options));
        }
        return new QuestionStats(examId, submissions.size(), items);
    }

    public Top top(UUID examId, int limit) {
        requireMockExam(examId);
        if (limit < 1 || limit > 100) {
            throw MockExamStatisticsException.badRequest("limit phải nằm trong khoảng 1 đến 100.");
        }

        List<MockTopData> ranked = bestSubmissions(submissionRepository.findMockTopDataByExamId(examId)).stream()
                .filter(item -> scoreFor(item) != null)
                .sorted(BEST_SUBMISSION_FIRST)
                .limit(limit)
                .toList();
        List<TopItem> items = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            MockTopData item = ranked.get(index);
            items.add(new TopItem(
                    index + 1,
                    item.getUserId(),
                    item.getFullName(),
                    item.getSubmissionId(),
                    scoreFor(item),
                    item.getTotalCorrect(),
                    item.getTotalQuestions(),
                    durationSeconds(item),
                    toVietnamTime(item.getSubmittedAt())));
        }
        return new Top(examId, limit, items);
    }

    private Exam requireMockExam(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> MockExamStatisticsException.notFound("Không tìm thấy đề thi thử."));
        if (exam.getExamType() != ExamType.MOCK) {
            throw MockExamStatisticsException.notFound("Không tìm thấy đề thi thử.");
        }
        return exam;
    }

    private List<ScoreBucket> distribution(List<Double> scores, String parameter) {
        List<Double> boundaries = parseBuckets(parameter);
        List<ScoreBucket> result = new ArrayList<>(boundaries.size());
        for (int index = 0; index < boundaries.size() - 1; index++) {
            double min = boundaries.get(index);
            double max = boundaries.get(index + 1);
            long count = scores.stream().filter(score -> score >= min && score < max).count();
            result.add(new ScoreBucket(
                    formatBoundary(min) + "-" + formatBoundary(max),
                    min,
                    max,
                    count,
                    percentOrZero(count, scores.size())));
        }
        long perfectCount = scores.stream().filter(score -> score == 10d).count();
        result.add(new ScoreBucket("10", 10d, 10d, perfectCount, percentOrZero(perfectCount, scores.size())));
        return result;
    }

    private List<Double> parseBuckets(String parameter) {
        if (parameter == null || parameter.isBlank()) {
            return DEFAULT_BUCKETS;
        }
        try {
            List<Double> values = java.util.Arrays.stream(parameter.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .toList();
            if (values.size() < 2
                    || values.get(0) != 0d
                    || values.get(values.size() - 1) != 10d
                    || values.stream().anyMatch(value -> !Double.isFinite(value) || value < 0d || value > 10d)) {
                throw new IllegalArgumentException();
            }
            for (int index = 1; index < values.size(); index++) {
                if (values.get(index) <= values.get(index - 1)) {
                    throw new IllegalArgumentException();
                }
            }
            return values;
        } catch (IllegalArgumentException e) {
            throw MockExamStatisticsException.badRequest(
                    "buckets phải là các mốc tăng dần từ 0 đến 10, ví dụ 0,3,4,5,6,7,8,9,10.");
        }
    }

    private static int partOrder(ExamPart part) {
        return part == null || part.getOrderIndex() == null ? 0 : part.getOrderIndex();
    }

    private static Double median(List<Double> sortedScores) {
        if (sortedScores.isEmpty()) {
            return null;
        }
        int middle = sortedScores.size() / 2;
        if (sortedScores.size() % 2 == 1) {
            return sortedScores.get(middle);
        }
        return roundScore((sortedScores.get(middle - 1) + sortedScores.get(middle)) / 2d);
    }

    private static Double rawScoreFor(MockTopData submission) {
        if (submission.getTotalCorrect() == null || submission.getTotalQuestions() == null
                || submission.getTotalQuestions() <= 0) {
            return null;
        }
        return submission.getTotalCorrect() * 10d / submission.getTotalQuestions();
    }

    private static Double scoreFor(MockTopData submission) {
        Double score = rawScoreFor(submission);
        return score == null ? null : roundScore(score);
    }

    private static Long durationSeconds(MockTopData submission) {
        LocalDateTime startedAt = submission.getStartedAt();
        LocalDateTime submittedAt = submission.getSubmittedAt();
        if (startedAt == null || submittedAt == null) {
            return null;
        }
        return Math.max(0L, Duration.between(startedAt, submittedAt).getSeconds());
    }

    private static Double percent(long count, long total) {
        return total == 0 ? null : roundScore(count * 100d / total);
    }

    private static double percentOrZero(long count, long total) {
        return total == 0 ? 0d : roundScore(count * 100d / total);
    }

    private static double roundScore(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String formatBoundary(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static OffsetDateTime toVietnamTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ServerTime.ZONE_ID).toOffsetDateTime();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw MockExamStatisticsException.badRequest("page phải >= 0 và size phải nằm trong khoảng 1 đến 100.");
        }
    }

    private static final Comparator<MockTopData> BEST_SUBMISSION_FIRST =
            Comparator.comparing(
                            MockExamStatisticsService::rawScoreFor,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(MockTopData::getSubmittedAt)
                    .thenComparing(MockExamStatisticsService::durationSeconds)
                    .thenComparing(MockTopData::getSubmissionId);

    private static List<MockTopData> bestSubmissions(Collection<MockTopData> submissions) {
        Map<SubmissionOwner, MockTopData> best = new HashMap<>();
        submissions.stream()
                .forEach(submission -> best.merge(
                        new SubmissionOwner(submission.getExamId(), submission.getUserId()),
                        submission,
                        (current, candidate) ->
                                BEST_SUBMISSION_FIRST.compare(candidate, current) < 0 ? candidate : current));
        return new ArrayList<>(best.values());
    }

    private static Double average(List<Double> values) {
        OptionalDouble result = values.stream()
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average();
        return result.isPresent() ? roundScore(result.getAsDouble()) : null;
    }

    private record SubmissionOwner(UUID examId, UUID userId) {
    }
}
