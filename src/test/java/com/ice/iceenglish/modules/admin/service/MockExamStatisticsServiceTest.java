package com.ice.iceenglish.modules.admin.service;

import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.Overview;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.QuestionItem;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.Top;
import com.ice.iceenglish.modules.admin.exception.MockExamStatisticsException;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository.MockTopData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockExamStatisticsServiceTest {

    private final ExamRepository exams = mock(ExamRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubDetailRepository details = mock(SubDetailRepository.class);
    private final MockExamStatisticsService service =
            new MockExamStatisticsService(exams, questions, submissions, details);

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(exams, questions, submissions, details);
    }

    @Test
    void returnsEmptyMetricsAndEveryDefaultBucketWithoutSubmissions() {
        UUID examId = UUID.randomUUID();
        when(exams.findById(examId)).thenReturn(Optional.of(mockExam(examId)));
        when(submissions.findMockTopDataByExamId(examId)).thenReturn(List.of());
        when(questions.countByExamId(examId)).thenReturn(2L);

        Overview result = service.overview(examId, null);

        assertEquals(0, result.submittedCount());
        assertEquals(2, result.totalQuestions());
        assertNull(result.averageScore());
        assertNull(result.highestScore());
        assertNull(result.lowestScore());
        assertNull(result.medianScore());
        assertNull(result.averageDurationSeconds());
        assertEquals(List.of("0-3", "3-4", "4-5", "5-6", "6-7", "7-8", "8-9", "9-10", "10"),
                result.scoreDistribution().stream().map(bucket -> bucket.label()).toList());
        assertEquals(0d, result.scoreDistribution().get(0).percent());
    }

    @Test
    void placesBoundaryScoresIntoHalfOpenBucketsAndPerfectScoreSeparately() {
        UUID examId = UUID.randomUUID();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(exams.findById(examId)).thenReturn(Optional.of(mockExam(examId)));
        List<MockTopData> rows = List.of(
                submission(examId, firstUser, 3, 10, 0),
                submission(examId, secondUser, 10, 10, 1));
        when(submissions.findMockTopDataByExamId(examId)).thenReturn(rows);
        when(questions.countByExamId(examId)).thenReturn(10L);

        Overview result = service.overview(examId, "0,3,10");

        assertEquals(6.5, result.averageScore());
        assertEquals(3d, result.scoreDistribution().get(1).min());
        assertEquals(1, result.scoreDistribution().get(1).count());
        assertEquals("10", result.scoreDistribution().get(2).label());
        assertEquals(1, result.scoreDistribution().get(2).count());
    }

    @Test
    void leaderboardKeepsOnlyEachStudentsHighestScoreAndUsesStableTieBreakers() {
        UUID examId = UUID.randomUUID();
        UUID firstStudent = UUID.randomUUID();
        UUID secondStudent = UUID.randomUUID();
        UUID lowScoreSubmissionId = UUID.randomUUID();
        UUID firstTopSubmissionId = UUID.randomUUID();
        UUID secondTopSubmissionId = UUID.randomUUID();
        when(exams.findById(examId)).thenReturn(Optional.of(mockExam(examId)));
        List<MockTopData> rows = List.of(
                submission(examId, firstStudent, 5, 10, 0, lowScoreSubmissionId),
                submission(examId, firstStudent, 9, 10, 20, firstTopSubmissionId),
                submission(examId, secondStudent, 9, 10, 10, secondTopSubmissionId));
        when(submissions.findMockTopDataByExamId(examId)).thenReturn(rows);

        Top result = service.top(examId, 10);

        assertEquals(2, result.items().size());
        assertEquals(secondTopSubmissionId, result.items().get(0).submissionId());
        assertEquals(firstTopSubmissionId, result.items().get(1).submissionId());
        assertEquals(9.0, result.items().get(0).score());
        assertEquals(1, result.items().get(0).rank());
        assertEquals(2, result.items().get(1).rank());
    }

    @Test
    void questionStatsCountOnlyAnsweredOptionsAndPreserveUnansweredSeparately() {
        UUID examId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID unansweredUserId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        Question question = Question.builder()
                .id(questionId)
                .orderIndex(1)
                .content("Question")
                .correctOpt("A")
                .build();
        SubDetailRepository.MockQuestionAnswerData answer = mock(
                SubDetailRepository.MockQuestionAnswerData.class);
        when(answer.getQuestionId()).thenReturn(questionId);
        when(answer.getSelectedOpt()).thenReturn("A");
        when(answer.getCorrect()).thenReturn(true);
        when(exams.findById(examId)).thenReturn(Optional.of(mockExam(examId)));
        List<MockTopData> rows = List.of(
                submission(examId, userId, 1, 1, 0, submissionId),
                submission(examId, unansweredUserId, 0, 1, 1));
        when(submissions.findMockTopDataByExamId(examId)).thenReturn(rows);
        when(questions.findAllWithDetailsByExamId(examId)).thenReturn(List.of(question));
        when(details.findMockQuestionAnswerDataBySubmissionIds(
                org.mockito.ArgumentMatchers.<UUID>anyList()))
                .thenReturn(List.of(answer));

        List<QuestionItem> result = service.questions(examId).questions();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).answeredCount());
        assertEquals(1, result.get(0).unansweredCount());
        assertEquals(1, result.get(0).correctCount());
        assertEquals(0, result.get(0).wrongCount());
        assertEquals(100d, result.get(0).optionStats().get(0).percent());
        assertEquals(4, result.get(0).optionStats().size());
    }

    @Test
    void rejectsNonMockExamAndOutOfRangeLeaderboardLimit() {
        UUID examId = UUID.randomUUID();
        when(exams.findById(examId)).thenReturn(Optional.of(
                Exam.builder().id(examId).examType(ExamType.GENERAL_EXAM).build()));
        assertThrows(MockExamStatisticsException.class, () -> service.top(examId, 10));

        when(exams.findById(examId)).thenReturn(Optional.of(mockExam(examId)));
        assertThrows(MockExamStatisticsException.class, () -> service.top(examId, 101));
    }

    private static Exam mockExam(UUID examId) {
        return Exam.builder()
                .id(examId)
                .title("Mock exam")
                .examType(ExamType.MOCK)
                .build();
    }

    private static MockTopData submission(
            UUID examId, UUID userId, int correct, int total, int elapsedMinutes) {
        return submission(examId, userId, correct, total, elapsedMinutes, UUID.randomUUID());
    }

    private static MockTopData submission(
            UUID examId, UUID userId, int correct, int total, int elapsedMinutes, UUID submissionId) {
        MockTopData submission = mock(MockTopData.class);
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 1, 10, 0).plusMinutes(elapsedMinutes);
        when(submission.getExamId()).thenReturn(examId);
        when(submission.getUserId()).thenReturn(userId);
        when(submission.getSubmissionId()).thenReturn(submissionId);
        when(submission.getTotalCorrect()).thenReturn(correct);
        when(submission.getTotalQuestions()).thenReturn(total);
        when(submission.getStartedAt()).thenReturn(startedAt);
        when(submission.getSubmittedAt()).thenReturn(startedAt.plusMinutes(30));
        when(submission.getFullName()).thenReturn("Student");
        return submission;
    }
}
