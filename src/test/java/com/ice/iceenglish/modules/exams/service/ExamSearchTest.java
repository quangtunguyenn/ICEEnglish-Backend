package com.ice.iceenglish.modules.exams.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExamSearchTest {
        private final ExamRepository exams = mock(ExamRepository.class);
        private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        private final ExamService service = new ExamService(
                        exams, mock(ExamPartRepository.class), mock(VideoRepository.class),
                        mock(ExamImportValidator.class), new ObjectMapper(),
                        new CacheRegistry(120, transactions), transactions);
        private final Sort sort = Sort.by(Sort.Order.desc("createdAt"));
        private List<Exam> candidates;

        @BeforeEach
        void setUp() {
                candidates = List.of(
                                exam("Đề thi thử Tốt nghiệp THPT", ExamType.MOCK, ExamStatus.PUBLISHED),
                                exam("Đề số 3 - Thầy Thiện", ExamType.MOCK, ExamStatus.PUBLISHED),
                                exam("de luyen tap", ExamType.GENERAL_EXAM, ExamStatus.PUBLISHED),
                                exam("Đề cũ", ExamType.MOCK, ExamStatus.DRAFT));
                when(exams.findByExamTypeAndStatus(ExamType.MOCK, ExamStatus.PUBLISHED, sort))
                                .thenReturn(candidates.subList(0, 2));
        }

        @Test
        void searchesVietnameseTitlesWithoutDiacriticsAndNormalizesD() {
                assertEquals("Đề thi thử Tốt nghiệp THPT",
                                service.searchExams(ExamType.MOCK, ExamStatus.PUBLISHED,
                                                "  de   thi thu ", PageRequest.of(0, 10, sort))
                                                .getContent().get(0).getTitle());
                assertEquals("Đề thi thử Tốt nghiệp THPT",
                                service.searchExams(ExamType.MOCK, ExamStatus.PUBLISHED,
                                                "tot nghiep", PageRequest.of(0, 10, sort))
                                                .getContent().get(0).getTitle());
                assertEquals("Đề số 3 - Thầy Thiện",
                                service.searchExams(ExamType.MOCK, ExamStatus.PUBLISHED,
                                                "DE SO 3", PageRequest.of(0, 10, sort))
                                                .getContent().get(0).getTitle());
        }

        @Test
        void searchesDiacriticKeywordAgainstTitlesWithAndWithoutDiacritics() {
                when(exams.findByStatus(ExamStatus.PUBLISHED, sort))
                                .thenReturn(List.of(candidates.get(0), candidates.get(2)));

                Page<Exam> result = service.searchExams(
                                null, ExamStatus.PUBLISHED, "đề", PageRequest.of(0, 10, sort));

                assertEquals(List.of("Đề thi thử Tốt nghiệp THPT", "de luyen tap"),
                                result.getContent().stream().map(Exam::getTitle).toList());
        }

        @Test
        void filtersBeforePaginationAndCountsMatchedResults() {
                when(exams.findByStatus(ExamStatus.PUBLISHED, sort))
                                .thenReturn(List.of(candidates.get(0), candidates.get(1), candidates.get(2)));

                Page<Exam> result = service.searchExams(
                                null, ExamStatus.PUBLISHED, "de", PageRequest.of(1, 1, sort));

                assertEquals(1, result.getContent().size());
                assertEquals("Đề số 3 - Thầy Thiện", result.getContent().get(0).getTitle());
                assertEquals(3, result.getTotalElements());
                assertEquals(3, result.getTotalPages());
        }

        @Test
        void blankKeywordDoesNotRequireTextSearchAndStillPaginates() {
                Page<Exam> result = service.searchExams(
                                ExamType.MOCK, ExamStatus.PUBLISHED, "  ", PageRequest.of(1, 1, sort));

                assertEquals(1, result.getContent().size());
                assertEquals(2, result.getTotalElements());
                assertEquals("Đề số 3 - Thầy Thiện", result.getContent().get(0).getTitle());
                verify(exams).findByExamTypeAndStatus(ExamType.MOCK, ExamStatus.PUBLISHED, sort);
        }

        private Exam exam(String title, ExamType type, ExamStatus status) {
                return Exam.builder().id(UUID.randomUUID()).title(title).examType(type).status(status).build();
        }
}
