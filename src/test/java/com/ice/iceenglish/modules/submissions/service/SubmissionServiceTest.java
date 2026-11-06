package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.HomeworkReviewResponse;
import com.ice.iceenglish.modules.submissions.dto.PracticeHistoryResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmissionAnswer;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.entity.Submission;
import com.ice.iceenglish.modules.submissions.repository.PartTimeLogRepository;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubDetailRepository subDetailRepository;
    @Mock private PartTimeLogRepository partTimeLogRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private ExamPartRepository examPartRepository;
    @Mock private ExamRepository examRepository;
    @Mock private ExamSnapshotService examSnapshotService;
    @Mock private UserRepository userRepository;
    @InjectMocks private SubmissionService service;

    private final UUID examId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID videoId = UUID.randomUUID();
    private final UUID correctId = UUID.randomUUID();
    private final UUID incorrectId = UUID.randomUUID();
    private final UUID unansweredId = UUID.randomUUID();
    private final UUID noVideoId = UUID.randomUUID();
    private final UUID clozePartId = UUID.randomUUID();
    private final UUID grammarPartId = UUID.randomUUID();
    private Exam exam;
    private Submission submission;
    private ExamSnapshot snapshot;

    @BeforeEach
    void setUp() {
        exam = Exam.builder().id(examId).title("Practice").build();
        User user = User.builder().id(userId).username("student").role("STUDENT").build();
        submission = Submission.builder()
                .id(submissionId).exam(exam).user(user).totalQuestions(4).totalCorrect(2).build();
        ExamSnapshot.VideoSnap video = new ExamSnapshot.VideoSnap(videoId, "Video", "https://example.com/video");
        snapshot = new ExamSnapshot(examId, "Practice", ExamType.GENERAL_EXAM,
                ExamStatus.PUBLISHED, null, List.of(
                new ExamSnapshot.PartSnap(clozePartId, "Cloze", PartType.CLOZE_TEST, "Cloze passage", null, 1),
                new ExamSnapshot.PartSnap(grammarPartId, "Grammar", PartType.GRAMMAR, null, null, 2)), List.of(
                question(correctId, 1, clozePartId, PartType.CLOZE_TEST, video),
                question(incorrectId, 2, clozePartId, PartType.CLOZE_TEST, video),
                question(unansweredId, 3, grammarPartId, PartType.GRAMMAR, video),
                question(noVideoId, 4, grammarPartId, PartType.GRAMMAR, null)));

        lenient().when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        lenient().when(subDetailRepository.findAnswersBySubmissionId(submissionId)).thenReturn(List.of(
                new SubmissionAnswer(correctId, "A", true),
                new SubmissionAnswer(incorrectId, "B", false),
                new SubmissionAnswer(unansweredId, null, false),
                new SubmissionAnswer(noVideoId, "A", true)));
        lenient().when(examSnapshotService.find(examId)).thenReturn(Optional.of(snapshot));
        lenient().when(partTimeLogRepository.findBySubmissionIdWithPart(submissionId)).thenReturn(List.of());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void practiceReviewIncludesEveryLinkedVideoRegardlessOfAnswerVisibility(boolean revealed) throws Exception {
        exam.setAnswersRevealed(revealed);
        when(submissionRepository.findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                userId, examId, "GENERAL_EXAM")).thenReturn(Optional.of(submission));

        ExamResultResponse result = service.reviewLatestPractice(examId, "student");

        assertEquals(4, result.getDetails().size());
        for (int i = 0; i < 3; i++) {
            ExamResultResponse.QuestionResultDto detail = result.getDetails().get(i);
            assertNotNull(detail.getRelatedVideo());
            assertEquals(videoId, detail.getRelatedVideo().getVideoId());
            assertEquals("Video", detail.getRelatedVideo().getVideoTitle());
            assertEquals("https://example.com/video", detail.getRelatedVideo().getVideoUrl());
            assertEquals(revealed ? "A" : null, detail.getCorrectOpt());
            assertEquals(revealed ? "Explanation" : null, detail.getExplanation());
        }
        assertNull(result.getDetails().get(3).getRelatedVideo());
        assertEquals(revealed ? "A" : null, result.getDetails().get(3).getCorrectOpt());
        assertEquals(revealed ? "Explanation" : null, result.getDetails().get(3).getExplanation());
        assertTrue(result.getDetails().get(0).getIsCorrect());
        assertFalse(result.getDetails().get(1).getIsCorrect());
        assertNull(result.getDetails().get(2).getSelectedOpt());

        assertReviewContent(result);
    }

    @Test
    void practiceHistoryReturnsOnlyPagedSubmissionSummariesWithUtcTimestamp() {
        exam.setExamType(ExamType.GENERAL_EXAM);
        submission.setSubmittedAt(LocalDateTime.of(2026, 11, 6, 14, 30));
        PageImpl<Submission> page = new PageImpl<>(
                List.of(submission), PageRequest.of(0, 20), 1);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(submissionRepository.findByUserUsernameAndExamIdAndModeAndSubmittedAtIsNotNull(
                eq("student"), eq(examId), eq("GENERAL_EXAM"), any(Pageable.class)))
                .thenReturn(page);

        PracticeHistoryResponse result = service.getPracticeHistory(examId, "student", 0, 20);

        assertEquals(examId, result.getExamId());
        assertEquals("Practice", result.getExamTitle());
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(1, result.getItems().size());
        assertEquals(submissionId, result.getItems().get(0).getSubmissionId());
        assertEquals(0, result.getItems().get(0).getSubmittedAt().getOffset().getTotalSeconds());
        assertEquals(0.5, result.getItems().get(0).getScore());
    }

    @Test
    void reviewPracticeSubmissionUsesRequestedOwnedSubmissionAndRevealPolicy() {
        exam.setAnswersRevealed(false);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 11, 6, 14, 30);
        submission.setSubmittedAt(submittedAt);
        when(submissionRepository.findByIdAndUserUsernameAndModeAndSubmittedAtIsNotNull(
                submissionId, "student", "GENERAL_EXAM")).thenReturn(Optional.of(submission));

        ExamResultResponse result = service.reviewPracticeSubmission(submissionId, "student");

        assertEquals(submissionId, result.getSubmissionId());
        assertEquals(0, result.getSubmittedAt().getOffset().getTotalSeconds());
        assertEquals(submittedAt.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                result.getSubmittedAt().toInstant());
        assertEquals(4, result.getDetails().size());
        assertNull(result.getDetails().get(0).getCorrectOpt());
        assertNull(result.getDetails().get(0).getExplanation());
        assertNull(result.getDetails().get(2).getSelectedOpt());
    }

    @Test
    void practiceReviewIncludesQuestionsWithoutStoredAnswerRows() {
        when(submissionRepository.findByIdAndUserUsernameAndModeAndSubmittedAtIsNotNull(
                submissionId, "student", "GENERAL_EXAM")).thenReturn(Optional.of(submission));
        when(subDetailRepository.findAnswersBySubmissionId(submissionId)).thenReturn(List.of());

        ExamResultResponse result = service.reviewPracticeSubmission(submissionId, "student");

        assertEquals(4, result.getDetails().size());
        assertTrue(result.getDetails().stream().allMatch(detail ->
                detail.getSelectedOpt() == null && Boolean.FALSE.equals(detail.getIsCorrect())));
    }

    @Test
    void reviewPracticeSubmissionReturnsNotFoundForUnownedOrUnsubmittedSubmission() {
        when(submissionRepository.findByIdAndUserUsernameAndModeAndSubmittedAtIsNotNull(
                submissionId, "student", "GENERAL_EXAM")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reviewPracticeSubmission(submissionId, "student"));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void submittedPracticeExamIdsReturnsDistinctMatchingIds() {
        when(submissionRepository.findSubmittedPracticeExamIds("student", List.of(examId)))
                .thenReturn(List.of(examId));

        assertEquals(Set.of(examId),
                service.getSubmittedPracticeExamIds("student", List.of(examId)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mockReviewIncludesPartsOptionsAndAllLinkedVideos(boolean revealed) {
        exam.setAnswersRevealed(revealed);
        submission.setMode("MOCK");
        when(submissionRepository.findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                userId, examId, "MOCK")).thenReturn(Optional.of(submission));
        when(examSnapshotService.find(examId)).thenReturn(Optional.of(new ExamSnapshot(
                examId, "Mock", ExamType.MOCK, ExamStatus.PUBLISHED, 60, snapshot.parts(), snapshot.questions())));

        ExamResultResponse result = service.reviewMockExam(examId, "student");

        assertEquals(4, result.getTotalQuestions());
        assertEquals(2, result.getTotalCorrect());
        assertEquals(0.5, result.getScore());
        assertReviewContent(result);
        for (int i = 0; i < 4; i++) {
            ExamResultResponse.QuestionResultDto detail = result.getDetails().get(i);
            assertEquals("A", detail.getCorrectOpt());
            assertEquals("Explanation", detail.getExplanation());
            if (i < 3) {
                assertNotNull(detail.getRelatedVideo());
                assertEquals(videoId, detail.getRelatedVideo().getVideoId());
                assertEquals("Video", detail.getRelatedVideo().getVideoTitle());
                assertEquals("https://example.com/video", detail.getRelatedVideo().getVideoUrl());
            } else {
                assertNull(detail.getRelatedVideo());
            }
        }
        assertTrue(result.getDetails().get(0).getIsCorrect());
        assertFalse(result.getDetails().get(1).getIsCorrect());
        assertNull(result.getDetails().get(2).getSelectedOpt());
    }

    private void assertReviewContent(ExamResultResponse result) {
        JsonNode json = JsonMapper.builder().build().valueToTree(result);
        assertEquals(2, json.get("parts").size());
        assertEquals(clozePartId.toString(), json.get("parts").get(0).get("partId").asText());
        assertEquals("Cloze", json.get("parts").get(0).get("partName").asText());
        assertEquals("CLOZE_TEST", json.get("parts").get(0).get("partType").asText());
        assertEquals(1, json.get("parts").get(0).get("orderIndex").asInt());
        assertEquals("Cloze passage", json.get("parts").get(0).get("passageText").asText());
        assertEquals(grammarPartId.toString(), json.get("parts").get(1).get("partId").asText());
        assertTrue(json.get("parts").get(1).get("passageText").isNull());
        for (int i = 0; i < 4; i++) {
            JsonNode detail = json.get("details").get(i);
            assertEquals("Question", detail.get("content").asText());
            assertEquals((i < 2 ? clozePartId : grammarPartId).toString(), detail.get("partId").asText());
            assertEquals("Option A", detail.get("optA").asText());
            assertEquals("Option B", detail.get("optB").asText());
            assertEquals("Option C", detail.get("optC").asText());
            assertEquals("Option D", detail.get("optD").asText());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL_EXAM", "MOCK"})
    void submitIncludesPartsAndOptionsForNewSubmission(String mode) {
        submission.setMode(mode);
        submission.setStartedAt(LocalDateTime.now());
        when(submissionRepository.findByIdAndUserUsernameForUpdate(submissionId, "student"))
                .thenReturn(Optional.of(submission));
        for (ExamSnapshot.QuestionSnap question : snapshot.questions()) {
            when(questionRepository.getReferenceById(question.id()))
                    .thenReturn(Question.builder().id(question.id()).build());
        }
        SubmitExamRequest request = request();
        request.setAnswers(List.of(answer(correctId, "A"), answer(incorrectId, "B"),
                answer(noVideoId, "A")));

        ExamResultResponse result = service.submitExam(request, "student");

        assertReviewContent(result);
        assertEquals(2, result.getTotalCorrect());
        assertEquals(0.5, result.getScore());
        assertEquals("A", result.getDetails().get(0).getSelectedOpt());
        assertEquals("A", result.getDetails().get(0).getCorrectOpt());
        assertEquals("Explanation", result.getDetails().get(0).getExplanation());
        assertNull(result.getDetails().get(0).getRelatedVideo());
        assertNotNull(result.getDetails().get(1).getRelatedVideo());
        assertNotNull(result.getDetails().get(2).getRelatedVideo());
        verify(submissionRepository).save(submission);
        verify(subDetailRepository).saveAll(anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL_EXAM", "MOCK"})
    void resubmissionIncludesPartsAndOptionsWithoutRegrading(String mode) {
        submission.setMode(mode);
        submission.setSubmittedAt(LocalDateTime.now());
        when(submissionRepository.findByIdAndUserUsernameForUpdate(submissionId, "student"))
                .thenReturn(Optional.of(submission));

        ExamResultResponse result = service.submitExam(request(), "student");

        assertReviewContent(result);
        assertEquals(2, result.getTotalCorrect());
        assertNull(result.getDetails().get(0).getRelatedVideo());
        assertNotNull(result.getDetails().get(1).getRelatedVideo());
        verify(submissionRepository, never()).save(any());
        verify(subDetailRepository, never()).saveAll(anyList());
    }

    @Test
    void homeworkSubmissionReturnsOptionsWithoutParts() {
        submission.setMode("HOMEWORK");
        submission.setSubmittedAt(LocalDateTime.now());
        when(submissionRepository.findByIdAndUserUsernameForUpdate(submissionId, "student"))
                .thenReturn(Optional.of(submission));
        ExamSnapshot.QuestionSnap question = question(correctId, 1, null, null, null);
        when(examSnapshotService.find(examId)).thenReturn(Optional.of(new ExamSnapshot(
                examId, "Homework", ExamType.HOMEWORK, ExamStatus.PUBLISHED, null, List.of(), List.of(question))));

        JsonNode json = JsonMapper.builder().build().valueToTree(service.submitHomework(request(), "student"));

        assertTrue(json.get("parts").isEmpty());
        JsonNode detail = json.get("details").get(0);
        assertFalse(detail.has("partId"));
        assertEquals("Option A", detail.get("optA").asText());
        assertEquals("Option B", detail.get("optB").asText());
        assertEquals("Option C", detail.get("optC").asText());
        assertEquals("Option D", detail.get("optD").asText());
    }

    private SubmitExamRequest request() {
        SubmitExamRequest request = new SubmitExamRequest();
        request.setSubmissionId(submissionId);
        request.setExamId(examId);
        return request;
    }

    private SubmitExamRequest.UserAnswerDto answer(UUID questionId, String selectedOpt) {
        SubmitExamRequest.UserAnswerDto answer = new SubmitExamRequest.UserAnswerDto();
        answer.setQuestionId(questionId);
        answer.setSelectedOpt(selectedOpt);
        return answer;
    }

    @Test
    void homeworkReviewIncludesOptionsAndOmitsVideos() {
        ExamSnapshot.VideoSnap video = new ExamSnapshot.VideoSnap(videoId, "Video", "https://example.com/video");
        when(examSnapshotService.find(examId)).thenReturn(Optional.of(new ExamSnapshot(
                examId, "Homework", ExamType.HOMEWORK, ExamStatus.PUBLISHED, null, List.of(), List.of(
                question(correctId, 1, null, null, video),
                question(incorrectId, 2, null, null, video),
                question(unansweredId, 3, null, null, video),
                question(noVideoId, 4, null, null, null)))));
        when(submissionRepository.findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                userId, examId, "HOMEWORK")).thenReturn(Optional.of(submission));

        HomeworkReviewResponse result = service.reviewLatestHomework(examId, "student");

        assertEquals(2, result.totalCorrect());
        assertEquals(0.5, result.score());
        JsonNode json = JsonMapper.builder().build().valueToTree(result);
        assertFalse(json.has("parts"));
        assertEquals(4, json.get("details").size());
        for (JsonNode detail : json.get("details")) {
            assertFalse(detail.has("partId"));
            assertFalse(detail.has("relatedVideo"));
            assertFalse(detail.has("related_video"));
            assertEquals("Question", detail.get("content").asText());
            assertEquals("Option A", detail.get("optA").asText());
            assertEquals("Option B", detail.get("optB").asText());
            assertEquals("Option C", detail.get("optC").asText());
            assertEquals("Option D", detail.get("optD").asText());
            assertEquals("A", detail.get("correctOpt").asText());
            assertEquals("Explanation", detail.get("explanation").asText());
        }
        assertTrue(json.get("details").get(0).get("isCorrect").asBoolean());
        assertFalse(json.get("details").get(1).get("isCorrect").asBoolean());
        assertTrue(json.get("details").get(2).get("selectedOpt").isNull());
    }

    private ExamSnapshot.QuestionSnap question(UUID id, int order, UUID partId,
                                               PartType partType, ExamSnapshot.VideoSnap video) {
        return new ExamSnapshot.QuestionSnap(id, order, "Question", "Option A", "Option B", "Option C",
                "Option D", "A", "Explanation", partId, partType, video);
    }
}
