package com.ice.iceenglish.modules.guest;

import com.ice.iceenglish.core.time.ServerTime;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
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
import com.ice.iceenglish.modules.videos.dto.VideoTitleResponse;
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
        service = new GuestService(examId.toString(), videoId.toString(), 30,
                snapshots, exams, videos, grading, clock);
        exam = new ExamSnapshot(examId, "Mock", ExamType.MOCK, ExamStatus.PUBLISHED, 1, true,
                List.of(new ExamSnapshot.PartSnap(partId, "Grammar", PartType.GRAMMAR, null, null, 1)),
                List.of(new ExamSnapshot.QuestionSnap(questionId, 1, "Question", "a", "b", "c", "d",
                        "A", "Explanation", partId, PartType.GRAMMAR,
                        new ExamSnapshot.VideoSnap(otherVideoId, "Locked", "https://example.com/locked"))));
        lenient().when(snapshots.find(examId)).thenReturn(Optional.of(exam));
    }

    @Test
    void gradesInMemoryWithServerTimingAndHidesLockedVideo() {
        StartExamResponse start = service.startExam(examId, null);
        UUID token = start.getSubmissionId();
        assertEquals(List.of(partId), start.getPartOrder());
        assertEquals(60, Duration.between(start.getStartedAt(), start.getExpiresAt()).getSeconds());
        service.track(examId, partId, token);
        clock.advance(10);
        service.track(examId, partId, token);
        assertTrue(service.partLogs(examId, token).isEmpty());
        clock.advance(5);
        SubmitExamRequest request = request(questionId, "B");
        ExamResultResponse result = service.submitExam(examId, token, request);
        assertEquals(0.0, result.getScore());
        assertEquals("A", result.getDetails().get(0).getCorrectOpt());
        assertEquals("Explanation", result.getDetails().get(0).getExplanation());
        assertNull(result.getDetails().get(0).getRelatedVideo());
        assertEquals(15, result.getPartTimeLogs().get(0).getTimeSpentSeconds());
        assertEquals(15, service.partLogs(examId, token).get(0).getTimeSpentSeconds());
        assertEquals(result, service.submitExam(examId, token, request(questionId, "A")));
        verifyNoInteractions(submissionRepository, detailRepository, logRepository);
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
    void listsAllVideoTitlesButOnlyReturnsSampleContent() {
        when(videos.getAllTitles()).thenReturn(List.of(
                VideoTitleResponse.builder().videoId(videoId).title("Sample").build(),
                VideoTitleResponse.builder().videoId(otherVideoId).title("Locked").build()));
        List<GuestService.GuestVideo> list = service.listVideos();
        assertTrue(list.get(0).accessible());
        assertFalse(list.get(1).accessible());
        assertThrows(ResponseStatusException.class, () -> service.learningContent(otherVideoId));
        assertThrows(ResponseStatusException.class, () -> service.vocabularies(otherVideoId));
        verify(videos, never()).getLearningContent(otherVideoId);
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
