package com.ice.iceenglish.modules.exams.controller;

import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExamShuffleControllerTest {
    @Test
    void rendersAttemptOrderWithoutMutatingCachedExam() {
        ExamService exams = mock(ExamService.class);
        SubmissionService submissions = mock(SubmissionService.class);
        Authentication auth = mock(Authentication.class);
        ExamController controller = new ExamController(exams, submissions);
        UUID examId = UUID.randomUUID(), submissionId = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), qa = UUID.randomUUID(), qb = UUID.randomUUID();
        ExamResponseDto.ExamPartDto pa = ExamResponseDto.ExamPartDto.builder().id(a)
                .questions(List.of(ExamResponseDto.QuestionDto.builder().id(qa).build())).build();
        ExamResponseDto.ExamPartDto pb = ExamResponseDto.ExamPartDto.builder().id(b)
                .questions(List.of(ExamResponseDto.QuestionDto.builder().id(qb).build())).build();
        ExamResponseDto cached = ExamResponseDto.builder().id(examId).parts(List.of(pa, pb))
                .questions(List.of()).build();
        when(auth.getName()).thenReturn("student");
        when(auth.getAuthorities()).thenReturn(List.of());
        when(submissions.getOwnedPartOrder(examId, submissionId, "student")).thenReturn(List.of(b, a));
        when(exams.getExamDetail(examId, false)).thenReturn(cached);

        ExamResponseDto response = controller.getExamDetail(examId, submissionId, auth).getBody();
        assertNotNull(response);
        assertEquals(List.of(b, a), response.getParts().stream().map(ExamResponseDto.ExamPartDto::getId).toList());
        assertEquals(1, response.getParts().get(0).getDisplayIndex());
        assertEquals(1, response.getParts().get(0).getQuestions().get(0).getDisplayNumber());
        assertEquals(2, response.getParts().get(1).getQuestions().get(0).getDisplayNumber());
        assertEquals(List.of(a, b), cached.getParts().stream().map(ExamResponseDto.ExamPartDto::getId).toList());
        assertNull(pa.getDisplayIndex());
        assertNull(pa.getQuestions().get(0).getDisplayNumber());
        assertSame(cached, controller.getExamDetail(examId, null, auth).getBody());
    }
}
