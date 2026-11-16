package com.ice.iceenglish.modules.exams.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExamStatusServiceTest {

    @Test
    void republishesArchivedExamAndEvictsContentCaches() {
        ExamRepository exams = mock(ExamRepository.class);
        CacheRegistry caches = mock(CacheRegistry.class);
        UUID examId = UUID.randomUUID();
        Exam exam = Exam.builder().id(examId).status(ExamStatus.ARCHIVED).build();
        when(exams.findById(examId)).thenReturn(Optional.of(exam));
        ExamService service = new ExamService(
                exams, mock(ExamPartRepository.class), mock(VideoRepository.class),
                mock(ExamImportValidator.class), new ObjectMapper(), caches,
                mock(PlatformTransactionManager.class));

        service.publish(examId);

        assertEquals(ExamStatus.PUBLISHED, exam.getStatus());
        verify(caches).evictAllAfterCommit();
    }
}
