package com.ice.iceenglish.modules.exams.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExamImportServiceTest {

    @Test
    void importsJsonWithShufflePartsAndEvictsContentCaches() {
        ExamRepository exams = mock(ExamRepository.class);
        CacheRegistry caches = mock(CacheRegistry.class);
        ExamImportValidator validator = mock(ExamImportValidator.class);
        when(validator.validate(any())).thenReturn(new ExamImportValidator.Report(
                java.util.List.of(), java.util.List.of()));
        AtomicReference<Exam> savedExam = new AtomicReference<>();
        when(exams.save(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            savedExam.set(exam);
            return exam;
        });
        ExamService service = new ExamService(
                exams, mock(ExamPartRepository.class), mock(VideoRepository.class), validator,
                new ObjectMapper(), caches, mock(PlatformTransactionManager.class));
        MockMultipartFile file = new MockMultipartFile("file", "exam.json", "application/json", """
                {
                  "exam": {
                    "title": "Mock exam",
                    "exam_type": "MOCK",
                    "duration_minutes": 50,
                    "shuffle_parts": true
                  },
                  "parts": [{
                    "part_name": "Grammar",
                    "part_type": "GRAMMAR",
                    "order_index": 1,
                    "questions": [{
                      "order_index": 1,
                      "content": "Choose one.",
                      "opt_a": "A",
                      "opt_b": "B",
                      "opt_c": "C",
                      "opt_d": "D",
                      "correct_opt": "A"
                    }]
                  }]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        service.importExam(file, ExamType.MOCK, "teacher");

        assertTrue(savedExam.get().isShuffleParts());
        verify(caches).evictAllAfterCommit();
    }
}
