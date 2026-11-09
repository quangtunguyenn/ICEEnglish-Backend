package com.ice.iceenglish.modules.guest;

import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.AnswerUrlResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.service.PartOrder;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.dto.HomeworkAnswersResponse;
import com.ice.iceenglish.modules.videos.service.VideoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GuestService {
    public record GuestVideo(UUID videoId, String title, boolean accessible) {}
    public record GuestExam(UUID id, String title, ExamType mode, Integer durationMinutes, int totalQuestions) {}

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final int MAX_SESSIONS = 1000;
    private final Set<UUID> examIds;
    private final UUID videoId;
    private final ExamSnapshotService snapshots;
    private final ExamService exams;
    private final VideoService videos;
    private final SubmissionService submissions;
    private final Clock clock;
    private final long graceSeconds;
    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();

    public GuestService(@Value("${app.guest.exam-ids:}") String examIds,
            @Value("${app.guest.video-id:}") String videoId,
            @Value("${app.exam.submit-grace-seconds:30}") long graceSeconds,
            ExamSnapshotService snapshots, ExamService exams, VideoService videos,
            SubmissionService submissions, Clock clock) {
        this.examIds = Arrays.stream(examIds.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
        this.videoId = videoId.isBlank() ? null : UUID.fromString(videoId.trim());
        this.graceSeconds = graceSeconds;
        this.snapshots = snapshots;
        this.exams = exams;
        this.videos = videos;
        this.submissions = submissions;
        this.clock = clock;
    }

    public List<GuestExam> listExams(ExamType mode) {
        if (mode != ExamType.GENERAL_EXAM && mode != ExamType.MOCK) {
            throw error(HttpStatus.BAD_REQUEST, "mode phải là GENERAL_EXAM hoặc MOCK.");
        }
        return examIds.stream().sorted().map(snapshots::find).flatMap(java.util.Optional::stream)
                .filter(e -> e.status() == ExamStatus.PUBLISHED && e.examType() == mode)
                .map(e -> new GuestExam(e.examId(), e.title(), e.examType(),
                        e.durationMinutes(), e.totalQuestions()))
                .toList();
    }

    public List<GuestVideo> listVideos() {
        return videos.getAllTitles().stream()
                .map(v -> new GuestVideo(v.getVideoId(), v.getTitle(), v.getVideoId().equals(videoId)))
                .toList();
    }

    public VideoLearningContentResponse learningContent(UUID id) {
        requireVideo(id);
        VideoLearningContentResponse content = videos.getLearningContent(id);
        return VideoLearningContentResponse.builder().videoId(content.getVideoId())
                .title(content.getTitle()).videoUrl(content.getVideoUrl())
                .documentUrl(content.getDocumentUrl())
                .homework(content.getHomework() == null ? null : withoutAnswerUrl(content.getHomework()))
                .vocabularies(content.getVocabularies()).build();
    }

    public List<VocabularyResponseDto> vocabularies(UUID id) {
        requireVideo(id);
        return videos.getVocabularies(id);
    }

    public StartExamResponse startExam(UUID id, UUID token) {
        return start(requireSampleExam(id), token);
    }

    public StartExamResponse startHomework(UUID id, UUID token) {
        return start(requireHomework(id), token);
    }

    private StartExamResponse start(ExamSnapshot exam, UUID token) {
        if (token != null) {
            Attempt attempt = requireAttempt(token, exam.examId());
            synchronized (attempt) {
                if (attempt.result != null) {
                    throw error(HttpStatus.CONFLICT, "Lượt làm đã được nộp.");
                }
                ensureActive(attempt, exam, LocalDateTime.now(clock), false);
                return startResponse(attempt, exam);
            }
        }
        if (exam.examType() == ExamType.MOCK
                && (exam.durationMinutes() == null || exam.durationMinutes() < 1)) {
            throw error(HttpStatus.CONFLICT, "Đề thi chưa có thời lượng hợp lệ.");
        }
        Attempt attempt = new Attempt(exam, LocalDateTime.now(clock));
        synchronized (attempts) {
            LocalDateTime now = LocalDateTime.now(clock);
            attempts.values().removeIf(a -> !now.isBefore(a.startedAt.plus(SESSION_TTL)));
            if (attempts.size() >= MAX_SESSIONS) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "Đã đạt giới hạn phiên khách, vui lòng thử lại sau.");
            }
            attempts.put(attempt.token, attempt);
        }
        return startResponse(attempt, exam);
    }

    public ExamResponseDto examDetail(UUID id, UUID token) {
        ExamSnapshot exam = requireSampleExam(id);
        Attempt attempt = requireAttempt(token, id);
        ExamResponseDto dto = withoutAnswerUrl(exams.getExamDetail(id, false));
        Map<UUID, ExamResponseDto.ExamPartDto> parts = dto.getParts().stream()
                .collect(Collectors.toMap(ExamResponseDto.ExamPartDto::getId, p -> p));
        Map<UUID, Integer> indexes = PartOrder.indexes(attempt.order);
        int[] number = {0};
        List<ExamResponseDto.ExamPartDto> ordered = attempt.order.stream().map(parts::get)
                .map(part -> part.toBuilder().displayIndex(indexes.get(part.getId()))
                        .questions(part.getQuestions().stream()
                                .map(q -> q.toBuilder().displayNumber(++number[0]).build()).toList())
                        .build()).toList();
        return dto.toBuilder().parts(ordered).build();
    }

    public void track(UUID id, UUID partId, UUID token) {
        ExamSnapshot exam = requireSampleExam(id);
        Attempt attempt = requireAttempt(token, id);
        synchronized (attempt) {
            LocalDateTime now = LocalDateTime.now(clock);
            ensureActive(attempt, exam, now, false);
            if (!exam.partIds().contains(partId)) {
                throw error(HttpStatus.BAD_REQUEST, "Part không thuộc đề thi.");
            }
            if (partId.equals(attempt.activePart)) return;
            accumulate(attempt, now);
            attempt.activePart = partId;
            attempt.partStartedAt = now;
        }
    }

    public ExamResultResponse submitExam(UUID id, UUID token, SubmitExamRequest request) {
        return submit(requireSampleExam(id), token, request);
    }

    public List<ExamResultResponse.PartTimeLogDto> partLogs(UUID id, UUID token) {
        ExamSnapshot exam = requireSampleExam(id);
        Attempt attempt = requireAttempt(token, id);
        synchronized (attempt) {
            if (attempt.result != null) return attempt.result.getPartTimeLogs();
            return logs(attempt, exam, LocalDateTime.now(clock), false);
        }
    }

    public AnswerUrlResponse answerUrl(UUID id, UUID token) {
        requireSampleExam(id);
        Attempt attempt = requireAttempt(token, id);
        synchronized (attempt) {
            if (attempt.result == null) {
                throw error(HttpStatus.FORBIDDEN, "Chỉ xem được đáp án sau khi nộp bài.");
            }
            String url = exams.getExamDetail(id, false).getAnswersUrl();
            if (url == null || url.isBlank()) {
                throw error(HttpStatus.NOT_FOUND, "Đề thi chưa có answer_url.");
            }
            return AnswerUrlResponse.builder().answerUrl(url).build();
        }
    }

    public ExamResultResponse submitHomework(UUID id, UUID token, SubmitExamRequest request) {
        return submit(requireHomework(id), token, request);
    }

    public HomeworkAnswersResponse homeworkAnswers(UUID id, UUID token) {
        ExamSnapshot homework = requireHomework(id);
        Attempt attempt = requireAttempt(token, homework.examId());
        synchronized (attempt) {
            if (attempt.result == null) {
                throw error(HttpStatus.FORBIDDEN, "Chỉ xem được đáp án sau khi nộp bài.");
            }
            return videos.getHomeworkAnswers(id);
        }
    }

    private ExamResultResponse submit(ExamSnapshot exam, UUID token, SubmitExamRequest request) {
        if (request == null) throw error(HttpStatus.BAD_REQUEST, "Thiếu nội dung bài nộp.");
        Attempt attempt = requireAttempt(token, exam.examId());
        synchronized (attempt) {
            if (attempt.result != null) return attempt.result;
            LocalDateTime now = LocalDateTime.now(clock);
            ensureActive(attempt, exam, now, true);
            List<ExamResultResponse.PartTimeLogDto> logs = logs(attempt, exam, now, true);
            try {
                attempt.result = submissions.gradeGuest(exam, attempt.order, attempt.startedAt,
                        now, request, logs, videoId);
            } catch (IllegalArgumentException ex) {
                throw error(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
            return attempt.result;
        }
    }

    private List<ExamResultResponse.PartTimeLogDto> logs(
            Attempt attempt, ExamSnapshot exam, LocalDateTime now, boolean includeActive) {
        Map<UUID, Integer> totals = new HashMap<>(attempt.seconds);
        if (includeActive && attempt.activePart != null) {
            int elapsed = Math.toIntExact(Math.max(0, Duration.between(attempt.partStartedAt, now).getSeconds()));
            if (elapsed > 0) totals.merge(attempt.activePart, elapsed, Math::addExact);
        }
        Map<UUID, String> names = exam.parts().stream()
                .collect(Collectors.toMap(ExamSnapshot.PartSnap::id, ExamSnapshot.PartSnap::name));
        return attempt.order.stream().filter(totals::containsKey)
                .map(id -> ExamResultResponse.PartTimeLogDto.builder().partId(id)
                        .partName(names.get(id)).timeSpentSeconds(totals.get(id)).build()).toList();
    }

    private void accumulate(Attempt attempt, LocalDateTime now) {
        if (attempt.activePart != null) {
            int elapsed = Math.toIntExact(Math.max(0, Duration.between(attempt.partStartedAt, now).getSeconds()));
            if (elapsed > 0) attempt.seconds.merge(attempt.activePart, elapsed, Math::addExact);
        }
    }

    private void ensureActive(Attempt attempt, ExamSnapshot exam, LocalDateTime now, boolean submit) {
        if (attempt.result != null) throw error(HttpStatus.CONFLICT, "Lượt làm đã được nộp.");
        if (exam.examType() == ExamType.MOCK && !now.isBefore(
                attempt.startedAt.plusMinutes(exam.durationMinutes()).plusSeconds(submit ? graceSeconds : 0))) {
            throw error(HttpStatus.CONFLICT, "Lượt thi đã hết thời gian.");
        }
    }

    private StartExamResponse startResponse(Attempt attempt, ExamSnapshot exam) {
        Map<UUID, ExamSnapshot.PartSnap> parts = exam.parts().stream()
                .collect(Collectors.toMap(ExamSnapshot.PartSnap::id, p -> p));
        Map<UUID, Integer> indexes = PartOrder.indexes(attempt.order);
        Integer limit = exam.examType() == ExamType.MOCK ? exam.durationMinutes() : null;
        return StartExamResponse.builder().submissionId(attempt.token).examId(exam.examId())
                .mode(exam.examType().name()).totalQuestions(exam.totalQuestions())
                .timeLimitMinutes(limit).serverNow(OffsetDateTime.now(clock))
                .startedAt(attempt.startedAt.atZone(clock.getZone()).toOffsetDateTime())
                .expiresAt(limit == null ? null : attempt.startedAt.plusMinutes(limit)
                        .atZone(clock.getZone()).toOffsetDateTime())
                .partOrder(attempt.order).parts(attempt.order.stream().map(parts::get)
                        .map(p -> StartExamResponse.PartTimeDto.builder().partId(p.id())
                                .partName(p.name()).orderIndex(p.orderIndex())
                                .displayIndex(indexes.get(p.id()))
                                .recommendedTimeSeconds(p.partType().getStandardTimeSeconds()).build())
                        .toList()).build();
    }

    private ExamSnapshot requireSampleExam(UUID id) {
        if (!examIds.contains(id)) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đề mẫu.");
        ExamSnapshot exam = requirePublished(id);
        if (exam.examType() != ExamType.GENERAL_EXAM && exam.examType() != ExamType.MOCK) {
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đề mẫu.");
        }
        return exam;
    }

    private ExamSnapshot requireHomework(UUID id) {
        requireVideo(id);
        UUID homeworkId = videos.getHomework(id).getId();
        ExamSnapshot homework = requirePublished(homeworkId);
        if (homework.examType() != ExamType.HOMEWORK) {
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy homework mẫu.");
        }
        return homework;
    }

    private ExamSnapshot requirePublished(UUID id) {
        return snapshots.find(id).filter(e -> e.status() == ExamStatus.PUBLISHED)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy nội dung mẫu."));
    }

    private void requireVideo(UUID id) {
        if (!id.equals(videoId)) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy video mẫu.");
    }

    private Attempt requireAttempt(UUID token, UUID examId) {
        Attempt attempt = token == null ? null : attempts.get(token);
        if (attempt == null || !examId.equals(attempt.examId)) {
            throw error(HttpStatus.NOT_FOUND, "Phiên khách không tồn tại hoặc đã hết hạn.");
        }
        if (!LocalDateTime.now(clock).isBefore(attempt.startedAt.plus(SESSION_TTL))) {
            if (attempt != null) attempts.remove(token, attempt);
            throw error(HttpStatus.NOT_FOUND, "Phiên khách không tồn tại hoặc đã hết hạn.");
        }
        return attempt;
    }

    private static ExamResponseDto withoutAnswerUrl(ExamResponseDto dto) {
        return dto.toBuilder().answersUrl(null).build();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private static final class Attempt {
        private final UUID token = UUID.randomUUID();
        private final UUID examId;
        private final LocalDateTime startedAt;
        private final List<UUID> order;
        private final Map<UUID, Integer> seconds = new HashMap<>();
        private UUID activePart;
        private LocalDateTime partStartedAt;
        private ExamResultResponse result;

        private Attempt(ExamSnapshot exam, LocalDateTime startedAt) {
            this.examId = exam.examId();
            this.startedAt = startedAt;
            this.order = exam.shuffleParts() && exam.examType() != ExamType.HOMEWORK
                    ? PartOrder.shuffled(exam) : new ArrayList<>(exam.parts().stream()
                            .map(ExamSnapshot.PartSnap::id).toList());
        }
    }
}
