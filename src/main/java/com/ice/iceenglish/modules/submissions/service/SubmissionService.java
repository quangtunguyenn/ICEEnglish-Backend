package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.AnswerUrlResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmissionAnswer;
import com.ice.iceenglish.modules.submissions.dto.SubmissionScoreResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.dto.TrackPartTimeRequest;
import com.ice.iceenglish.modules.submissions.entity.SubDetail;
import com.ice.iceenglish.modules.submissions.entity.Submission;
import com.ice.iceenglish.modules.submissions.entity.PartTimeLog;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.PartTimeLogRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubDetailRepository subDetailRepository;
    private final PartTimeLogRepository partTimeLogRepository;
    private final QuestionRepository questionRepository;
    private final ExamPartRepository examPartRepository;
    private final ExamRepository examRepository;
    private final ExamSnapshotService examSnapshotService;
    private final UserRepository userRepository;

    /** Thời gian ân hạn cho phép nộp muộn, bù cho độ trễ khi nhiều học sinh nộp đúng lúc hết giờ. */
    @Value("${app.exam.submit-grace-seconds:30}")
    private long submitGraceSeconds;

    @Transactional
    public StartExamResponse startExam(UUID examId, String username) {
        // Khóa dòng học sinh trước (chống tạo trùng lượt thi khi bấm nhiều lần) và kiểm tra role bằng chính dòng này.
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đăng nhập."));
        assertStudent(user);
        ExamSnapshot exam = examSnapshotService.find(examId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đề thi."));
        if (exam.status() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("Đề thi chưa được xuất bản.");
        }
        String normalizedMode = defaultMode(exam.examType());
        Integer timeLimitMinutes = exam.examType() == ExamType.MOCK ? exam.durationMinutes() : null;
        if ("MOCK".equals(normalizedMode)
                && (timeLimitMinutes == null || timeLimitMinutes < 1)) {
            throw new IllegalStateException("Đề MOCK chưa có thời lượng hợp lệ.");
        }
        int totalQuestions = exam.totalQuestions();

        if ("MOCK".equals(normalizedMode)) {
            Optional<Submission> existingMock = submissionRepository
                    .findFirstByUserUsernameAndExamIdAndModeOrderByStartedAtDesc(
                            username, examId, "MOCK");
            if (existingMock.isPresent()) {
                if (existingMock.get().getSubmittedAt() != null
                        || isExpired(existingMock.get())) {
                    throw new IllegalStateException("Học sinh chỉ được thi thử đề này một lần.");
                }
                return toStartExamResponse(existingMock.get(), exam);
            }
        } else {
            Optional<Submission> existingSubmission = submissionRepository
                    .findFirstByUserUsernameAndExamIdAndSubmittedAtIsNullOrderByStartedAtDesc(
                            username, examId);
            if (existingSubmission.isPresent() && !isExpired(existingSubmission.get())) {
                return toStartExamResponse(existingSubmission.get(), exam);
            }
        }

        LocalDateTime startedAt = LocalDateTime.now();
        Submission submission = Submission.builder()
                .user(user)
                .exam(examRepository.getReferenceById(examId))
                .mode(normalizedMode)
                .timeLimitMinutes(timeLimitMinutes)
                .totalQuestions(totalQuestions)
                .totalCorrect(0)
                .startedAt(startedAt)
                .build();
        Submission saved = submissionRepository.save(submission);
        return toStartExamResponse(saved, exam);
    }

    @Transactional
    public StartExamResponse startHomeworkByVideo(UUID videoId, String username) {
        Exam homework = findHomeworkByVideo(videoId);
        return startExam(homework.getId(), username);
    }

    @Transactional
    public ExamResultResponse submitHomework(SubmitExamRequest request, String username) {
        validateRequest(request);
        ExamSnapshot homework = examSnapshotService.find(request.getExamId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy homework."));
        if (homework.examType() != ExamType.HOMEWORK) {
            throw new IllegalArgumentException("exam_id không phải là homework.");
        }
        return submitExam(request, username);
    }

    @Transactional(readOnly = true)
    public SubmissionScoreResponse getLatestPracticeScore(UUID examId, String username) {
        User user = requireStudentAndGet(username);
        Submission submission = submissionRepository
                .findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                        user.getId(), examId, "GENERAL_EXAM")
                .orElseThrow(() -> new IllegalArgumentException("Chưa có kết quả luyện đề."));
        return toScoreResponse(submission);
    }

    /** Chi tiết bài luyện đề (GENERAL_EXAM) gần nhất; chỉ lộ đáp án đúng/lời giải khi đề đã công bố đáp án. */
    @Transactional(readOnly = true)
    public ExamResultResponse reviewLatestPractice(UUID examId, String username) {
        User user = requireStudentAndGet(username);
        Submission submission = submissionRepository
                .findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                        user.getId(), examId, "GENERAL_EXAM")
                .orElseThrow(() -> new IllegalArgumentException("Chưa có kết quả luyện đề."));
        List<SubmissionAnswer> answers = subDetailRepository.findAnswersBySubmissionId(submission.getId());
        ExamResultResponse result = toResultResponse(submission, answers);
        if (!submission.getExam().isAnswersRevealed()) {
            result.getDetails().forEach(d -> {
                d.setCorrectOpt(null);
                d.setExplanation(null);
                d.setRelatedVideo(null);
            });
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ExamResultResponse reviewLatestHomework(UUID examId, String username) {
        User user = requireStudentAndGet(username);
        Submission submission = submissionRepository
                .findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                        user.getId(), examId, "HOMEWORK")
                .orElseThrow(() -> new IllegalArgumentException("Chưa có kết quả bài tập về nhà."));
        List<SubmissionAnswer> answers = subDetailRepository.findAnswersBySubmissionId(submission.getId());
        return toResultResponse(submission, answers);
    }

    @Transactional(readOnly = true)
    public List<SubmissionScoreResponse> getMockScores(String username) {
        User user = requireStudentAndGet(username);
        return submissionRepository
                .findByUserIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtAsc(
                        user.getId(), "MOCK")
                .stream()
                .map(this::toScoreResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamResultResponse reviewMockExam(UUID examId, String username) {
        User user = requireStudentAndGet(username);
        Submission submission = submissionRepository
                .findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                        user.getId(), examId, "MOCK")
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài thi thử đã nộp."));
        List<SubmissionAnswer> answers = subDetailRepository.findAnswersBySubmissionId(submission.getId());
        return toResultResponse(submission, answers);
    }

    @Transactional(readOnly = true)
    public AnswerUrlResponse getAnswerUrl(UUID examId, String username) {
        User user = requireStudentAndGet(username);
        Submission submission = submissionRepository
                .findFirstByUserIdAndExamIdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                        user.getId(), examId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Chưa tìm thấy bài nộp cho đề thi."));
        String answerUrl = submission.getExam().getAnswersUrl();
        if (answerUrl == null || answerUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Đề thi chưa có answer_url.");
        }
        return AnswerUrlResponse.builder()
                .answerUrl(answerUrl)
                .build();
    }

    private boolean isExpired(Submission submission) {
        return submission.getTimeLimitMinutes() != null
                && !LocalDateTime.now().isBefore(
                submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes()));
    }

    private StartExamResponse toStartExamResponse(Submission submission, ExamSnapshot exam) {
        return StartExamResponse.builder()
                .submissionId(submission.getId())
                .examId(exam.examId())
                .mode(submission.getMode())
                .totalQuestions(exam.totalQuestions())
                .timeLimitMinutes(submission.getTimeLimitMinutes())
                .startedAt(submission.getStartedAt())
                .expiresAt(submission.getTimeLimitMinutes() == null
                        ? null : submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes()))
                .parts(exam.parts().stream()
                        .map(part -> StartExamResponse.PartTimeDto.builder()
                                .partId(part.id())
                                .partName(part.name())
                                .orderIndex(part.orderIndex())
                                .recommendedTimeSeconds(part.partType().getStandardTimeSeconds())
                                .build())
                        .toList())
                .build();
    }

    private String defaultMode(ExamType examType) {
        return switch (examType) {
            case MOCK -> "MOCK";
            case HOMEWORK -> "HOMEWORK";
            case GENERAL_EXAM -> "GENERAL_EXAM";
        };
    }

    @Transactional
    public void trackPartTime(UUID submissionId, UUID partId,
                              TrackPartTimeRequest request, String username) {
        requireStudent(username);
        if (request == null || request.getTimeSpentSeconds() == null
                || request.getTimeSpentSeconds() < 0) {
            throw new IllegalArgumentException("timeSpentSeconds phải >= 0.");
        }
        Submission submission = submissionRepository.findByIdAndUserUsername(submissionId, username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt thi."));
        if (!"MOCK".equals(submission.getMode())) {
            throw new IllegalStateException("Chỉ thi thử (MOCK) mới tracking thời gian theo part.");
        }
        if (submission.getSubmittedAt() != null) {
            throw new IllegalStateException("Lượt thi này đã được nộp.");
        }
        if (!LocalDateTime.now().isBefore(
                submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes()))) {
            throw new IllegalStateException("Lượt thi đã hết thời gian.");
        }
        if (!ensureSnapshot(submission).partIds().contains(partId)) {
            throw new IllegalArgumentException("Part không thuộc đề thi.");
        }
        PartTimeLog log = partTimeLogRepository.findBySubmissionIdAndPartId(submissionId, partId)
                .orElseGet(() -> PartTimeLog.builder()
                        .submission(submission)
                        .part(examPartRepository.getReferenceById(partId))
                        .build());
        log.setTimeSpentSeconds(request.getTimeSpentSeconds());
        partTimeLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<ExamResultResponse.PartTimeLogDto> getPartTimeLogs(
            UUID submissionId, String username) {
        requireStudent(username);
        Submission submission = submissionRepository.findByIdAndUserUsername(submissionId, username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt thi."));
        return partTimeLogs(submission);
    }

    @Transactional
    public ExamResultResponse submitExam(SubmitExamRequest request, String username) {
        requireStudent(username);
        validateRequest(request);
        Submission submission = submissionRepository
                .findByIdAndUserUsernameForUpdate(request.getSubmissionId(), username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt thi"));
        UUID examId = submission.getExam().getId();
        if (!examId.equals(request.getExamId())) {
            throw new IllegalArgumentException("exam_id không khớp với submission.");
        }
        if (submission.getSubmittedAt() != null) {
            // Nộp lặp (timeout mạng, bấm lại): trả lại kết quả đã chấm thay vì báo lỗi, không chấm lại.
            return toResultResponse(submission,
                    subDetailRepository.findAnswersBySubmissionId(submission.getId()));
        }
        if (submission.getTimeLimitMinutes() != null
                && !LocalDateTime.now().isBefore(
                submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes())
                        .plusSeconds(submitGraceSeconds))) {
            throw new IllegalStateException("Lượt thi đã hết thời gian.");
        }

        ExamSnapshot exam = examSnapshotService.find(examId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đề thi."));
        List<ExamSnapshot.QuestionSnap> questions = exam.questions();

        Map<UUID, String> userAnswers = mapAnswers(request, exam.questionsById());

        int totalQuestions = questions.size();
        int totalCorrect = 0;

        List<SubDetail> detailsToSave = new ArrayList<>(totalQuestions);

        // Tiến hành chấm điểm từng câu
        for (ExamSnapshot.QuestionSnap q : questions) {
            String selectedOpt = userAnswers.getOrDefault(q.id(), null);
            boolean isCorrect = selectedOpt != null
                    && q.correctOpt() != null
                    && q.correctOpt().equalsIgnoreCase(selectedOpt);

            if (isCorrect) {
                totalCorrect++;
            }

            detailsToSave.add(SubDetail.builder()
                    .submission(submission)
                    .question(questionRepository.getReferenceById(q.id()))
                    .selectedOpt(selectedOpt)
                    .isCorrect(isCorrect)
                    .build());
        }

        submission.setTotalQuestions(totalQuestions);
        submission.setTotalCorrect(totalCorrect);
        submission.setSubmittedAt(LocalDateTime.now());

        submissionRepository.save(submission);
        subDetailRepository.saveAll(detailsToSave);
        savePartTimeLogs(request, submission, exam);

        return toResultResponse(submission, exam, detailsToSave.stream()
                .map(d -> new SubmissionAnswer(d.getQuestion().getId(), d.getSelectedOpt(), d.getIsCorrect()))
                .toList());
    }

    /** Map questionId -> partType, chỉ cho câu đầu tiên của mỗi part READING_8 / READING_10. */
    private Map<UUID, String> firstQuestionPartTypes(Collection<ExamSnapshot.QuestionSnap> questions) {
        Map<UUID, ExamSnapshot.QuestionSnap> firstByPart = new HashMap<>();
        for (ExamSnapshot.QuestionSnap question : questions) {
            if (question.partId() == null || (question.partType() != PartType.READING_8
                    && question.partType() != PartType.READING_10)) {
                continue;
            }
            firstByPart.merge(question.partId(), question, (current, candidate) ->
                    candidate.orderIndex() < current.orderIndex() ? candidate : current);
        }
        Map<UUID, String> result = new HashMap<>();
        firstByPart.values().forEach(question -> result.put(question.id(), question.partType().name()));
        return result;
    }

    private List<ExamResultResponse.PartTranslationDto> partTranslations(ExamSnapshot exam) {
        return exam.parts().stream()
                .filter(part -> (part.partType() == PartType.READING_8
                        || part.partType() == PartType.READING_10)
                        && (part.passageText() != null || part.translate() != null))
                .map(part -> ExamResultResponse.PartTranslationDto.builder()
                        .partId(part.id())
                        .partName(part.name())
                        .partType(part.partType().name())
                        .passageText(part.passageText())
                        .translate(part.translate())
                        .build())
                .toList();
    }

    private void validateRequest(SubmitExamRequest request) {
        if (request == null || request.getSubmissionId() == null) {
            throw new IllegalArgumentException("submissionId là bắt buộc.");
        }

        if (request.getExamId() == null) {
            throw new IllegalArgumentException("exam_id là bắt buộc.");
        }
    }

    private Exam findHomeworkByVideo(UUID videoId) {
        return examRepository.findByVideoIdAndExamTypeWithParts(
                        videoId, com.ice.iceenglish.modules.exams.enums.ExamType.HOMEWORK)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy homework của video."));
    }

    private void requireStudent(String username) {
        requireStudentAndGet(username);
    }

    private User requireStudentAndGet(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đăng nhập."));
        assertStudent(user);
        return user;
    }

    private void assertStudent(User user) {
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase(Locale.ROOT);
        if (!"STUDENT".equals(role)) {
            throw new IllegalStateException("Tài khoản phải có role STUDENT.");
        }
    }

    private SubmissionScoreResponse toScoreResponse(Submission submission) {
        int totalCorrect = Optional.ofNullable(submission.getTotalCorrect()).orElse(0);
        int totalQuestions = Optional.ofNullable(submission.getTotalQuestions()).orElse(0);
        return SubmissionScoreResponse.builder()
                .submissionId(submission.getId())
                .examId(submission.getExam().getId())
                .examTitle(submission.getExam().getTitle())
                .totalQuestions(totalQuestions)
                .totalCorrect(totalCorrect)
                .totalIncorrect(totalQuestions - totalCorrect)
                .score(totalCorrect * 0.25)
                .startedAt(submission.getStartedAt())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    private ExamResultResponse toResultResponse(Submission submission, List<SubmissionAnswer> answers) {
        UUID examId = submission.getExam().getId();
        ExamSnapshot exam = examSnapshotService.find(examId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đề thi: " + examId));
        return toResultResponse(submission, exam, answers);
    }

    private ExamResultResponse toResultResponse(
            Submission submission, ExamSnapshot exam, List<SubmissionAnswer> answers) {
        Map<UUID, SubmissionAnswer> answerByQuestion = new HashMap<>();
        answers.forEach(a -> answerByQuestion.put(a.questionId(), a));
        Map<UUID, String> firstQuestionPartTypes = firstQuestionPartTypes(exam.questions().stream()
                .filter(q -> answerByQuestion.containsKey(q.id()))
                .toList());
        List<ExamResultResponse.QuestionResultDto> resultDetails = exam.questions().stream()
                .filter(question -> answerByQuestion.containsKey(question.id()))
                .map(question -> {
                    SubmissionAnswer answer = answerByQuestion.get(question.id());
                    ExamSnapshot.VideoSnap video = question.video();
                    ExamResultResponse.RelatedVideoDto videoDto = !Boolean.TRUE.equals(answer.isCorrect())
                            && video != null
                            ? ExamResultResponse.RelatedVideoDto.builder()
                                    .videoId(video.id())
                                    .videoTitle(video.title())
                                    .videoUrl(video.url())
                                    .build()
                            : null;
                    return ExamResultResponse.QuestionResultDto.builder()
                            .questionId(question.id())
                            .orderIndex(question.orderIndex())
                            .content(question.content())
                            .selectedOpt(answer.selectedOpt())
                            .correctOpt(question.correctOpt())
                            .isCorrect(answer.isCorrect())
                            .explanation(question.explanation())
                            .partType(firstQuestionPartTypes.get(question.id()))
                            .relatedVideo(videoDto) // Gửi kèm video bài giảng nếu câu này có liên kết
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
                .score(totalCorrect * 0.25)
                .startedAt(submission.getStartedAt())
                .submittedAt(submission.getSubmittedAt())
                .partTimeLogs(partTimeLogs(submission))
                .partTranslations(partTranslations(exam))
                .details(resultDetails)
                .build();
    }

    private ExamSnapshot ensureSnapshot(Submission submission) {
        UUID examId = submission.getExam().getId();
        return examSnapshotService.find(examId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đề thi: " + examId));
    }

    private Map<UUID, String> mapAnswers(
            SubmitExamRequest request, Map<UUID, ExamSnapshot.QuestionSnap> questionMap) {
        Map<UUID, String> answers = new HashMap<>();
        for (SubmitExamRequest.UserAnswerDto answer : Optional.ofNullable(request.getAnswers())
                .orElseGet(Collections::emptyList)) {
            if (answer == null || answer.getQuestionId() == null) {
                throw new IllegalArgumentException("Mỗi đáp án phải có questionId.");
            }
            if (!questionMap.containsKey(answer.getQuestionId())) {
                throw new IllegalArgumentException(
                        "Câu hỏi " + answer.getQuestionId() + " không thuộc đề thi.");
            }
            if (answers.containsKey(answer.getQuestionId())) {
                throw new IllegalArgumentException("Một câu hỏi chỉ được gửi một đáp án.");
            }
            String selectedOpt = answer.getSelectedOpt();
            if (selectedOpt != null && !selectedOpt.isBlank()) {
                selectedOpt = selectedOpt.trim().toUpperCase(Locale.ROOT);
                if (!Set.of("A", "B", "C", "D").contains(selectedOpt)) {
                    throw new IllegalArgumentException("Đáp án chỉ được là A, B, C hoặc D.");
                }
            } else {
                selectedOpt = null;
            }
            answers.put(answer.getQuestionId(), selectedOpt);
        }
        return answers;
    }

    private void savePartTimeLogs(SubmitExamRequest request, Submission submission, ExamSnapshot exam) {
        if (request.getPartLogs() == null || request.getPartLogs().isEmpty()) {
            return;
        }
        Set<UUID> examPartIds = exam.partIds();
        Map<UUID, PartTimeLog> existingLogs = partTimeLogRepository
                .findBySubmissionIdOrderByCreatedAtAsc(submission.getId()).stream()
                .collect(Collectors.toMap(l -> l.getPart().getId(), Function.identity(), (a, b) -> a));
        List<PartTimeLog> logs = new ArrayList<>();
        for (SubmitExamRequest.PartTimeLogDto log : request.getPartLogs()) {
            if (log == null || log.getPartId() == null) {
                throw new IllegalArgumentException("Mỗi partLog phải có partId.");
            }
            if (!examPartIds.contains(log.getPartId())) {
                throw new IllegalArgumentException(
                        "Part " + log.getPartId() + " không thuộc đề thi của submission.");
            }
            if (log.getTimeSpentSeconds() == null || log.getTimeSpentSeconds() < 0) {
                throw new IllegalArgumentException(
                        "timeSpentSeconds của part " + log.getPartId() + " phải >= 0.");
            }
            PartTimeLog entity = existingLogs.get(log.getPartId());
            if (entity == null) {
                entity = PartTimeLog.builder()
                        .submission(submission)
                        .part(examPartRepository.getReferenceById(log.getPartId()))
                        .build();
                existingLogs.put(log.getPartId(), entity);
            }
            entity.setTimeSpentSeconds(log.getTimeSpentSeconds());
            logs.add(entity);
        }
        partTimeLogRepository.saveAll(logs);
    }

    private List<ExamResultResponse.PartTimeLogDto> partTimeLogs(Submission submission) {
        return partTimeLogRepository.findBySubmissionIdWithPart(submission.getId())
                .stream()
                .map(log -> ExamResultResponse.PartTimeLogDto.builder()
                        .partId(log.getPart().getId())
                        .partName(log.getPart().getPartName())
                        .timeSpentSeconds(log.getTimeSpentSeconds())
                        .build())
                .toList();
    }
}