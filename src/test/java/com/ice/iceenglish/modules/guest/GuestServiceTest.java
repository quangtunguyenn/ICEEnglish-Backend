package com.ice.iceenglish.modules.guest;

import com.ice.iceenglish.core.time.ServerTime;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.repository.PartTimeLogRepository;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.dto.VideoGuestTitleResponse;
import com.ice.iceenglish.modules.videos.dto.HomeworkAnswersResponse;
import com.ice.iceenglish.modules.videos.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuestServiceTest {
    private final UUID examId = UUID.randomUUID();
    private final UUID partId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID videoId = UUID.randomUUID();
    private final UUID otherVideoId = UUID.randomUUID();
    private final UUID homeworkId = UUID.randomUUID();
    private final ExamSnapshotService snapshots = mock(ExamSnapshotService.class);
    private final ExamService exams = mock(ExamService.class);
    private final ExamRepository examRepository = mock(ExamRepository.class);
    private final VideoService videos = mock(VideoService.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final SubDetailRepository detailRepository = mock(SubDetailRepository.class);
    private final PartTimeLogRepository logRepository = mock(PartTimeLogRepository.class);
    private final MutableClock clock = new MutableClock();
    private GuestService service;
    private ExamSnapshot exam;

    @BeforeEach
    void setUp() {
        SubmissionService grading = new SubmissionService(submissionRepository, detailRepository, logRepository,
                mock(QuestionRepository.class), mock(ExamPartRepository.class), mock(ExamRepository.class),
                snapshots, mock(UserRepository.class), clock);
        service = new GuestService(30,
                examRepository, snapshots, exams, videos, grading, clock);
        when(examRepository.findByStatusAndExamTypeOrderByCreatedAtDesc(
                ExamStatus.PUBLISHED, ExamType.MOCK)).thenReturn(List.of());
        when(examRepository.findByStatusAndExamTypeOrderByCreatedAtDesc(
                ExamStatus.PUBLISHED, ExamType.GENERAL_EXAM)).thenReturn(List.of());
        exam = new ExamSnapshot(examId, "Mock", ExamType.MOCK, ExamStatus.PUBLISHED, 1, true, true,
                List.of(new ExamSnapshot.PartSnap(partId, "Cloze", PartType.CLOZE_TEST,
                        null, null, 1, false, null, 240)),
                List.of(new ExamSnapshot.QuestionSnap(questionId, 1, "Question", "a", "b", "c", "d",
                        "A", "Explanation", partId, PartType.CLOZE_TEST,
                        new ExamSnapshot.VideoSnap(otherVideoId, "Locked", "https://example.com/locked"))));
        lenient().when(snapshots.find(examId)).thenReturn(Optional.of(exam));
    }

    @Test
    void gradesInMemoryWithServerTimingAndHidesLockedVideo() {
        StartExamResponse start = service.startExam(examId, null);
        UUID token = start.getSubmissionId();
        assertEquals(List.of(partId), start.getPartOrder());
        assertEquals(240, start.getParts().get(0).getRecommendedTimeSeconds());
        assertEquals(60, Duration.between(start.getStartedAt(), start.getExpiresAt()).getSeconds());
        service.track(examId, partId, token);
        clock.advance(10);
        service.track(examId, partId, token);
        assertEquals(10, service.partLogs(examId, token).get(0).getTimeSpentSeconds());
        clock.advance(5);
        SubmitExamRequest request = request(questionId, "B");
        ExamResultResponse result = service.submitExam(examId, token, request);
        assertEquals(0.0, result.getScore());
        assertEquals("A", result.getDetails().get(0).getCorrectOpt());
        assertEquals("Explanation", result.getDetails().get(0).getExplanation());
        assertNull(result.getDetails().get(0).getRelatedVideo());
        assertEquals(15, result.getPartTimeLogs().get(0).getTimeSpentSeconds());
        assertEquals(240, result.getPartTimeLogs().get(0).getRecommendedTimeSeconds());
        assertEquals(1, result.getPartTimeLogs().get(0).getDisplayIndex());
        assertEquals(15, service.partLogs(examId, token).get(0).getTimeSpentSeconds());
        assertEquals(result, service.submitExam(examId, token, request(questionId, "A")));
        verifyNoInteractions(submissionRepository, detailRepository, logRepository);
    }

    @Test
    void guestResultIncludesRelatedVideoWhenItIsGuestAccessible() {
        when(videos.getGuestAccessibleVideoIds()).thenReturn(Set.of(otherVideoId));
        UUID token = service.startExam(examId, null).getSubmissionId();

        ExamResultResponse result = service.submitExam(examId, token, request(questionId, "B"));

        assertEquals(otherVideoId, result.getDetails().get(0).getRelatedVideo().getVideoId());
    }

    @Test
    void validatesAnswersAndAllowsRetryAfterInvalidSubmission() {
        UUID token = service.startExam(examId, null).getSubmissionId();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submitExam(examId, token, request(UUID.randomUUID(), "A")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        ExamResultResponse result = service.submitExam(examId, token, request(questionId, "A"));
        assertEquals(0.25, result.getScore());
        assertTrue(result.getDetails().get(0).getIsCorrect());
    }

    @Test
    void enforcesExamWhitelistAndServerExpiryIncludingGrace() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.startExam(UUID.randomUUID(), null)).getStatusCode());
        UUID token = service.startExam(examId, null).getSubmissionId();
        clock.advance(60);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.track(examId, partId, token)).getStatusCode());
        clock.advance(29);
        assertNotNull(service.submitExam(examId, token, request(questionId, "A")));
        UUID second = service.startExam(examId, null).getSubmissionId();
        clock.advance(90);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.submitExam(examId, second, request(questionId, "A"))).getStatusCode());
    }

    @Test
    void listsAllVideoTitlesAndBlocksLockedContent() {
        when(videos.getAllGuestTitles()).thenReturn(List.of(
                new VideoGuestTitleResponse(videoId, "Sample", "1.1", true),
                new VideoGuestTitleResponse(otherVideoId, "Locked", null, false)));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Video này dành cho học viên ICE English."))
                .when(videos).requireGuestAccess(otherVideoId);

        List<GuestService.GuestVideo> list = service.listVideos();
        assertTrue(list.get(0).accessible());
        assertEquals("1.1", list.get(0).position());
        assertFalse(list.get(1).accessible());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.learningContent(otherVideoId)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.vocabularies(otherVideoId)).getStatusCode());
        verify(videos, never()).getLearningContent(otherVideoId);
        verify(videos, never()).getVocabularies(otherVideoId);
    }

    @Test
    void homeworkHidesAnswerUrlAndCanBeGradedWithoutWrites() {
        ExamResponseDto homework = ExamResponseDto.builder().id(homeworkId).answersUrl("secret").build();
        when(videos.getLearningContent(videoId)).thenReturn(VideoLearningContentResponse.builder()
                .videoId(videoId).title("Sample").homework(homework).vocabularies(List.of()).build());
        when(videos.getHomework(videoId)).thenReturn(homework);
        ExamSnapshot snapshot = new ExamSnapshot(homeworkId, "Homework", ExamType.HOMEWORK,
                ExamStatus.PUBLISHED, null, List.of(),
                List.of(new ExamSnapshot.QuestionSnap(questionId, 1, "Q", "a", "b", "c", "d",
                        "A", "Why", null, null, null)));
        when(snapshots.find(homeworkId)).thenReturn(Optional.of(snapshot));
        assertNull(service.learningContent(videoId).getHomework().getAnswersUrl());
        assertEquals("secret", homework.getAnswersUrl());
        UUID token = service.startHomework(videoId, null).getSubmissionId();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.homeworkAnswers(videoId, token)).getStatusCode());
        when(videos.getHomeworkAnswers(videoId)).thenReturn(HomeworkAnswersResponse.builder()
                .videoId(videoId).homeworkId(homeworkId).answersUrl("secret").build());
        assertEquals(0.25, service.submitHomework(videoId, token, request(questionId, "A")).getScore());
        assertEquals("secret", service.homeworkAnswers(videoId, token).getAnswersUrl());
        verifyNoInteractions(submissionRepository, detailRepository, logRepository);
    }

    @Test
    void resumeKeepsOriginalOrderAndExamDetailHidesAnswerUrl() {
        ExamResponseDto.QuestionDto question = ExamResponseDto.QuestionDto.builder()
                .id(questionId).orderIndex(1).content("Question").build();
        ExamResponseDto.ExamPartDto part = ExamResponseDto.ExamPartDto.builder()
                .id(partId).questions(List.of(question)).build();
        when(exams.getExamDetail(examId, false)).thenReturn(ExamResponseDto.builder()
                .id(examId).answersUrl("secret").parts(List.of(part)).build());
        StartExamResponse first = service.startExam(examId, null);
        clock.advance(10);
        StartExamResponse resumed = service.startExam(examId, first.getSubmissionId());
        assertEquals(first.getStartedAt(), resumed.getStartedAt());
        assertEquals(first.getPartOrder(), resumed.getPartOrder());
        ExamResponseDto detail = service.examDetail(examId, first.getSubmissionId());
        assertNull(detail.getAnswersUrl());
        assertEquals(1, detail.getParts().get(0).getDisplayIndex());
        assertEquals(1, detail.getParts().get(0).getQuestions().get(0).getDisplayNumber());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.answerUrl(examId, first.getSubmissionId())).getStatusCode());
        service.submitExam(examId, first.getSubmissionId(), request(questionId, "A"));
        assertEquals("secret", service.answerUrl(examId, first.getSubmissionId()).getAnswerUrl());
    }

    @Test
    void expiredSessionAndUnpublishedExamAreRejected() {
        UUID token = service.startExam(examId, null).getSubmissionId();
        clock.advance(24 * 60 * 60);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.submitExam(examId, token, request(questionId, "A"))).getStatusCode());
        ExamSnapshot draft = new ExamSnapshot(examId, "Draft", ExamType.MOCK, ExamStatus.DRAFT,
                1, List.of(), List.of());
        when(snapshots.find(examId)).thenReturn(Optional.of(draft));
        assertTrue(service.listExams(ExamType.MOCK).isEmpty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.startExam(examId, null)).getStatusCode());
    }

    @Test
    void guestListIncludesPublishedLockedExamsWithOnlySummaryFields() {
        Exam sample = Exam.builder().id(examId).title("Sample").examType(ExamType.MOCK)
                .status(ExamStatus.PUBLISHED).durationMinutes(60).guestAccessible(true).build();
        Exam locked = Exam.builder().id(UUID.randomUUID()).title("Locked").examType(ExamType.MOCK)
                .status(ExamStatus.PUBLISHED).guestAccessible(false).build();
        when(examRepository.findByStatusAndExamTypeOrderByCreatedAtDesc(
                ExamStatus.PUBLISHED, ExamType.MOCK)).thenReturn(List.of(sample, locked));
        when(examRepository.findQuestionCountsByExamIds(List.of(examId, locked.getId())))
                .thenReturn(List.of(questionCount(examId, 40L), questionCount(locked.getId(), 35L)));

        List<GuestService.GuestExam> listed = service.listExams(ExamType.MOCK);

        assertEquals(2, listed.size());
        assertTrue(listed.get(0).accessible());
        assertFalse(listed.get(1).accessible());
        assertEquals(40, listed.get(0).totalQuestions());
        assertEquals(35, listed.get(1).totalQuestions());
        verify(examRepository).findByStatusAndExamTypeOrderByCreatedAtDesc(
                ExamStatus.PUBLISHED, ExamType.MOCK);
    }

    @Test
    void rejectsEveryGuestExamOperationWhenExamIsLocked() {
        ExamSnapshot locked = new ExamSnapshot(examId, "Locked", ExamType.MOCK, ExamStatus.PUBLISHED,
                1, false, false, List.of(), List.of());
        when(snapshots.find(examId)).thenReturn(Optional.of(locked));
        UUID token = UUID.randomUUID();

        assertGuestExamLocked(() -> service.startExam(examId, null));
        assertGuestExamLocked(() -> service.examDetail(examId, token));
        assertGuestExamLocked(() -> service.track(examId, partId, token));
        assertGuestExamLocked(() -> service.partLogs(examId, token));
        assertGuestExamLocked(() -> service.submitExam(examId, token, request(questionId, "A")));
        assertGuestExamLocked(() -> service.answerUrl(examId, token));
    }

    private static ExamRepository.ExamQuestionCount questionCount(UUID id, long count) {
        return new ExamRepository.ExamQuestionCount() {
            @Override public UUID getExamId() { return id; }
            @Override public Long getTotalQuestions() { return count; }
        };
    }

    private static void assertGuestExamLocked(org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action);
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("Đề này dành cho học viên ICE English.", error.getReason());
    }

    private static SubmitExamRequest request(UUID question, String option) {
        SubmitExamRequest.UserAnswerDto answer = new SubmitExamRequest.UserAnswerDto();
        answer.setQuestionId(question);
        answer.setSelectedOpt(option);
        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(answer));
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-07T10:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ServerTime.ZONE_ID; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

}
