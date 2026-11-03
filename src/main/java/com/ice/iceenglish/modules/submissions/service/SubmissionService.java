package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmissionScoreResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.dto.TrackPartTimeRequest;
import com.ice.iceenglish.modules.submissions.entity.SubDetail;
import com.ice.iceenglish.modules.submissions.entity.Submission;
import com.ice.iceenglish.modules.submissions.entity.PartTimeLog;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.PartTimeLogRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final VideoRepository videoRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;

    @Transactional
    public StartExamResponse startExam(UUID examId, String username) {
        requireStudent(username);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đề thi."));
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("Đề thi chưa được xuất bản.");
        }
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học sinh."));
        String normalizedMode = defaultMode(exam);
        Integer timeLimitMinutes = exam.getExamType()
                == com.ice.iceenglish.modules.exams.enums.ExamType.MOCK
                ? exam.getDurationMinutes() : null;
        if ("MOCK".equals(normalizedMode)
                && (timeLimitMinutes == null || timeLimitMinutes < 1)) {
            throw new IllegalStateException("Đề MOCK chưa có thời lượng hợp lệ.");
        }
        int totalQuestions = questionRepository
                .findByPartExamIdOrderByOrderIndexAsc(examId).size();

        if ("MOCK".equals(normalizedMode)) {
            Optional<Submission> existingMock = submissionRepository
                    .findFirstByUserUsernameAndExamIdAndModeOrderByStartedAtDesc(
                            username, examId, "MOCK");
            if (existingMock.isPresent()) {
                if (existingMock.get().getSubmittedAt() != null
                        || isExpired(existingMock.get())) {
                    throw new IllegalStateException("Học sinh chỉ được thi thử đề này một lần.");
                }
                return toStartExamResponse(existingMock.get(), totalQuestions);
            }
        } else {
            Optional<Submission> existingSubmission = submissionRepository
                    .findFirstByUserUsernameAndExamIdAndSubmittedAtIsNullOrderByStartedAtDesc(
                            username, examId);
            if (existingSubmission.isPresent() && !isExpired(existingSubmission.get())) {
                return toStartExamResponse(existingSubmission.get(), totalQuestions);
            }
        }

        LocalDateTime startedAt = LocalDateTime.now();
        Submission submission = Submission.builder()
                .user(user)
                .exam(exam)
                .mode(normalizedMode)
                .timeLimitMinutes(timeLimitMinutes)
                .totalQuestions(totalQuestions)
                .totalCorrect(0)
                .startedAt(startedAt)
                .build();
        Submission saved = submissionRepository.save(submission);
        return toStartExamResponse(saved, totalQuestions);
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
        List<SubDetail> details = subDetailRepository.findBySubmissionId(submission.getId());
        return toResultResponse(submission, details);
    }

    private boolean isExpired(Submission submission) {
        return submission.getTimeLimitMinutes() != null
                && !LocalDateTime.now().isBefore(
                submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes()));
    }

    private StartExamResponse toStartExamResponse(Submission submission, int totalQuestions) {
        Exam exam = submission.getExam();
        return StartExamResponse.builder()
                .submissionId(submission.getId())
                .examId(exam.getId())
                .mode(submission.getMode())
                .totalQuestions(totalQuestions)
                .timeLimitMinutes(submission.getTimeLimitMinutes())
                .startedAt(submission.getStartedAt())
                .expiresAt(submission.getTimeLimitMinutes() == null
                        ? null : submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes()))
                .parts(exam.getParts().stream()
                        .map(part -> StartExamResponse.PartTimeDto.builder()
                                .partId(part.getId())
                                .partName(part.getPartName())
                                .orderIndex(part.getOrderIndex())
                                .recommendedTimeSeconds(part.getPartType().getStandardTimeSeconds())
                                .build())
                        .toList())
                .build();
    }

    private String defaultMode(Exam exam) {
        return switch (exam.getExamType()) {
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
        ExamPart part = submission.getExam().getParts().stream()
                .filter(candidate -> candidate.getId().equals(partId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Part không thuộc đề thi."));
        PartTimeLog log = partTimeLogRepository.findBySubmissionIdAndPartId(submissionId, partId)
                .orElseGet(() -> PartTimeLog.builder()
                        .submission(submission)
                        .part(part)
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
        if (!submission.getExam().getId().equals(request.getExamId())) {
            throw new IllegalArgumentException("exam_id không khớp với submission.");
        }
        if (submission.getSubmittedAt() != null) {
            throw new IllegalStateException("Lượt thi này đã được nộp.");
        }
        if (submission.getTimeLimitMinutes() != null
                && !LocalDateTime.now().isBefore(
                submission.getStartedAt().plusMinutes(submission.getTimeLimitMinutes()))) {
            throw new IllegalStateException("Lượt thi đã hết thời gian.");
        }

        List<Question> questions = questionRepository
                .findByPartExamIdOrderByOrderIndexAsc(submission.getExam().getId());
        Map<UUID, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        Map<UUID, String> userAnswers = mapAnswers(request, questionMap);

        int totalQuestions = questions.size();
        int totalCorrect = 0;

        List<SubDetail> detailsToSave = new ArrayList<>();
        List<ExamResultResponse.QuestionResultDto> resultDetails = new ArrayList<>();

        // Lấy video liên quan từ từng câu hỏi, không từ exam.
        Set<UUID> videoIds = questions.stream()
                .map(Question::getRelatedVideo)
                .filter(Objects::nonNull)
                .map(Video::getId)
                .collect(Collectors.toSet());
        
        Map<UUID, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, Function.identity()));

        // Tiến hành chấm điểm từng câu
        for (Question q : questions) {
            String selectedOpt = userAnswers.getOrDefault(q.getId(), null);
            boolean isCorrect = selectedOpt != null
                    && q.getCorrectOpt() != null
                    && q.getCorrectOpt().equalsIgnoreCase(selectedOpt);

            if (isCorrect) {
                totalCorrect++;
            }

            SubDetail detail = SubDetail.builder()
                    .submission(submission)
                    .question(q)
                    .selectedOpt(selectedOpt)
                    .isCorrect(isCorrect)
                    .build();
            detailsToSave.add(detail);

            ExamResultResponse.RelatedVideoDto videoDto = null;
            if (!isCorrect && q.getRelatedVideo() != null
                    && videoMap.containsKey(q.getRelatedVideo().getId())) {
                Video v = videoMap.get(q.getRelatedVideo().getId());
                videoDto = ExamResultResponse.RelatedVideoDto.builder()
                        .videoId(v.getId())
                        .videoTitle(v.getTitle())
                        .videoUrl(v.getVideoUrl())
                        .build();
            }

            resultDetails.add(ExamResultResponse.QuestionResultDto.builder()
                    .questionId(q.getId())
                    .orderIndex(q.getOrderIndex())
                    .content(q.getContent())
                    .selectedOpt(selectedOpt)
                    .correctOpt(q.getCorrectOpt())
                    .isCorrect(isCorrect)
                    .explanation(q.getExplanation())
                    .relatedVideo(videoDto) // Gửi kèm video bài giảng nếu câu này có liên kết
                    .build());
        }

        int totalIncorrect = totalQuestions - totalCorrect;
        double score = totalCorrect * 0.25;
        submission.setTotalQuestions(totalQuestions);
        submission.setTotalCorrect(totalCorrect);
        submission.setSubmittedAt(LocalDateTime.now());
        
        submissionRepository.save(submission);
        subDetailRepository.saveAll(detailsToSave);
        savePartTimeLogs(request, submission);

        return ExamResultResponse.builder()
                .submissionId(submission.getId())
                .examId(submission.getExam().getId())
                .examTitle(submission.getExam().getTitle())
                .totalQuestions(totalQuestions)
                .totalCorrect(totalCorrect)
                .totalIncorrect(totalIncorrect)
                .score(score)
                .startedAt(submission.getStartedAt())
                .submittedAt(submission.getSubmittedAt())
                .partTimeLogs(partTimeLogs(submission))
                .details(resultDetails)
                .build();
    }

    private void validateRequest(SubmitExamRequest request) {
        if (request == null || request.getSubmissionId() == null) {
            throw new IllegalArgumentException("submissionId là bắt buộc.");
        }

        if (request.getExamId() == null) {
            throw new IllegalArgumentException("exam_id là bắt buộc.");
        }
    }

    private void requireStudent(String username) {
        requireStudentAndGet(username);
    }

    private User requireStudentAndGet(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đăng nhập."));
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase(Locale.ROOT);
        if (!"STUDENT".equals(role)) {
            throw new IllegalStateException("Tài khoản phải có role STUDENT.");
        }
        return user;
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

    private ExamResultResponse toResultResponse(
            Submission submission, List<SubDetail> details) {
        Map<UUID, Video> videoMap = videoRepository.findAllById(details.stream()
                        .map(SubDetail::getQuestion)
                        .map(Question::getRelatedVideo)
                        .filter(Objects::nonNull)
                        .map(Video::getId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Video::getId, Function.identity()));
        List<ExamResultResponse.QuestionResultDto> resultDetails = details.stream()
                .map(detail -> {
                    Question question = detail.getQuestion();
                    Video video = question.getRelatedVideo() == null
                            ? null : videoMap.get(question.getRelatedVideo().getId());
                    ExamResultResponse.RelatedVideoDto videoDto = !Boolean.TRUE.equals(detail.getIsCorrect())
                            && video != null
                            ? ExamResultResponse.RelatedVideoDto.builder()
                                    .videoId(video.getId())
                                    .videoTitle(video.getTitle())
                                    .videoUrl(video.getVideoUrl())
                                    .build()
                            : null;
                    return ExamResultResponse.QuestionResultDto.builder()
                            .questionId(question.getId())
                            .orderIndex(question.getOrderIndex())
                            .content(question.getContent())
                            .selectedOpt(detail.getSelectedOpt())
                            .correctOpt(question.getCorrectOpt())
                            .isCorrect(detail.getIsCorrect())
                            .explanation(question.getExplanation())
                            .relatedVideo(videoDto)
                            .build();
                })
                .toList();
        int totalCorrect = Optional.ofNullable(submission.getTotalCorrect()).orElse(0);
        int totalQuestions = Optional.ofNullable(submission.getTotalQuestions()).orElse(0);
        return ExamResultResponse.builder()
                .submissionId(submission.getId())
                .examId(submission.getExam().getId())
                .examTitle(submission.getExam().getTitle())
                .totalQuestions(totalQuestions)
                .totalCorrect(totalCorrect)
                .totalIncorrect(totalQuestions - totalCorrect)
                .score(totalCorrect * 0.25)
                .startedAt(submission.getStartedAt())
                .submittedAt(submission.getSubmittedAt())
                .partTimeLogs(partTimeLogs(submission))
                .details(resultDetails)
                .build();
    }

    private Map<UUID, String> mapAnswers(SubmitExamRequest request, Map<UUID, Question> questionMap) {
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

    private void savePartTimeLogs(SubmitExamRequest request, Submission submission) {
        if (request.getPartLogs() == null || request.getPartLogs().isEmpty()) {
            return;
        }
        Set<UUID> examPartIds = submission.getExam().getParts().stream()
                .map(part -> part.getId())
                .collect(Collectors.toSet());
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
            PartTimeLog entity = partTimeLogRepository
                    .findBySubmissionIdAndPartId(submission.getId(), log.getPartId())
                    .orElseGet(() -> PartTimeLog.builder()
                            .submission(submission)
                            .part(submission.getExam().getParts().stream()
                                    .filter(part -> part.getId().equals(log.getPartId()))
                                    .findFirst()
                                    .orElseThrow())
                            .build());
            entity.setTimeSpentSeconds(log.getTimeSpentSeconds());
            logs.add(entity);
        }
        partTimeLogRepository.saveAll(logs);
    }

    private List<ExamResultResponse.PartTimeLogDto> partTimeLogs(Submission submission) {
        return partTimeLogRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId())
                .stream()
                .map(log -> ExamResultResponse.PartTimeLogDto.builder()
                        .partId(log.getPart().getId())
                        .partName(log.getPart().getPartName())
                        .timeSpentSeconds(log.getTimeSpentSeconds())
                        .build())
                .toList();
    }
}