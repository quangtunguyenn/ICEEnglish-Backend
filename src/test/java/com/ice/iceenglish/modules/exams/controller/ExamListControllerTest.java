package com.ice.iceenglish.modules.exams.controller;

import com.ice.iceenglish.modules.exams.dto.ExamListPageResponse;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamListControllerTest {

    private final ExamService examService = mock(ExamService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final ExamController controller = new ExamController(examService, submissionService);

    @Test
    void pagedStudentListForcesPublishedAndIncludesAttemptSummary() {
        UUID examId = UUID.randomUUID();
        Exam exam = Exam.builder().id(examId).examType(ExamType.GENERAL_EXAM)
                .status(ExamStatus.PUBLISHED).build();
        var examPage = new PageImpl<>(List.of(exam), PageRequest.of(0, 12), 1);
        var response = ExamListPageResponse.builder().items(List.of()).page(0).size(12)
                .totalElements(1).totalPages(1).build();
        when(examService.searchExams(eq(ExamType.GENERAL_EXAM), eq(ExamStatus.PUBLISHED),
                eq(""), any())).thenReturn(examPage);
        when(submissionService.getLatestScoresByExamIds(
                "student", "GENERAL_EXAM", List.of(examId))).thenReturn(Map.of(examId, 7.5));
        when(examService.toExamListPage(eq(examPage), eq(Map.of(examId, 7.5)), anySet()))
                .thenReturn(response);
        var authentication = new TestingAuthenticationToken(
                "student", "token", new SimpleGrantedAuthority("STUDENT"));

        var result = controller.listPublishedExams(ExamType.GENERAL_EXAM, "DRAFT",
                0, 12, "", "createdAt,desc", authentication);

        assertSame(response, result.getBody());
        verify(examService).searchExams(eq(ExamType.GENERAL_EXAM), eq(ExamStatus.PUBLISHED),
                eq(""), any());
        verify(submissionService).getLatestScoresByExamIds(
                "student", "GENERAL_EXAM", List.of(examId));
    }

    @Test
    void pagedStaffListWithoutStatusCanIncludeAllStatuses() {
        var examPage = new PageImpl<Exam>(List.of(), PageRequest.of(0, 12), 0);
        when(examService.searchExams(isNull(), isNull(), eq(""), any())).thenReturn(examPage);
        when(examService.toExamListPage(examPage, null, null))
                .thenReturn(ExamListPageResponse.builder().items(List.of()).build());
        var authentication = new TestingAuthenticationToken(
                "teacher", "token", new SimpleGrantedAuthority("ROLE_TEACHER"));

        controller.listPublishedExams(null, null, 0, 12, "", "createdAt,desc", authentication);

        verify(examService).searchExams(isNull(), isNull(), eq(""), any());
        verifyNoInteractions(submissionService);
    }

    @Test
    void pagedListRejectsSizeAboveLimit() {
        var authentication = new TestingAuthenticationToken(
                "student", "token", new SimpleGrantedAuthority("STUDENT"));

        assertThrows(IllegalArgumentException.class, () -> controller.listPublishedExams(
                ExamType.MOCK, null, 0, 101, "", "createdAt,desc", authentication));
    }

    @Test
    void studentStatusFilterIsIgnoredEvenWhenInvalid() {
        var response = ExamListPageResponse.builder().items(List.of()).build();
        var examPage = new PageImpl<Exam>(List.of(), PageRequest.of(0, 12), 0);
        when(examService.searchExams(eq(ExamType.MOCK), eq(ExamStatus.PUBLISHED),
                eq(""), any())).thenReturn(examPage);
        when(examService.toExamListPage(eq(examPage), eq(Map.of()), anySet())).thenReturn(response);
        var authentication = new TestingAuthenticationToken(
                "student", "token", new SimpleGrantedAuthority("STUDENT"));

        assertSame(response, controller.listPublishedExams(
                ExamType.MOCK, "not-a-status", 0, 12, "", "createdAt,desc", authentication).getBody());
    }
}
