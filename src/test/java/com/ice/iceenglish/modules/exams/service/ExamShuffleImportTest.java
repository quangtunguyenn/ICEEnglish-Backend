package com.ice.iceenglish.modules.exams.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.dto.ExamImportDto;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExamShuffleImportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOptionalFieldsAndRejectsWrongJsonTypes() throws Exception {
        ExamImportDto dto = mapper.readValue("""
                {"exam":{"shuffle_parts":true},"parts":[{"fixed_position":true,"shuffle_group":"A"}]}
                """, ExamImportDto.class);
        assertTrue(dto.getExam().getShuffleParts());
        assertTrue(dto.getParts().get(0).getFixedPosition());
        assertEquals("A", dto.getParts().get(0).getShuffleGroup());
        ExamImportDto legacy = mapper.readValue("{\"exam\":{},\"parts\":[{}]}", ExamImportDto.class);
        assertNull(legacy.getExam().getShuffleParts());
        assertNull(legacy.getParts().get(0).getFixedPosition());
        for (String invalid : List.of(
                "{\"exam\":{\"shuffle_parts\":\"true\"}}",
                "{\"exam\":{\"shuffle_parts\":1}}",
                "{\"parts\":[{\"fixed_position\":\"false\"}]}",
                "{\"parts\":[{\"shuffle_group\":3}]}")) {
            assertThrows(Exception.class, () -> mapper.readValue(invalid, ExamImportDto.class));
        }
    }

    @Test
    void blankGroupIsInvalid() {
        ExamImportDto dto = new ExamImportDto();
        ExamImportDto.PartImport part = new ExamImportDto.PartImport();
        part.setShuffleGroup("  ");
        dto.setParts(List.of(part));
        assertTrue(new ExamImportValidator().validate(dto).errors().stream()
                .anyMatch(error -> error.contains("shuffle_group")));
    }

    @Test
    void sampleImportsAndPreviewRetainsConfiguration() throws Exception {
        ExamRepository exams = mock(ExamRepository.class);
        ExamPartRepository parts = mock(ExamPartRepository.class);
        ExamService service = new ExamService(exams, parts, mock(VideoRepository.class),
                new ExamImportValidator(), mapper, mock(CacheRegistry.class),
                mock(PlatformTransactionManager.class));
        UUID id = UUID.randomUUID();
        when(exams.save(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            exam.setId(id);
            return exam;
        });
        MockMultipartFile file = new MockMultipartFile("file", "sample.json", "application/json",
                Files.readAllBytes(Path.of("docs", "exam-import-sample.json")));
        assertEquals(2, service.importExam(file, ExamType.GENERAL_EXAM, "teacher").getTotalParts());
        var saved = org.mockito.ArgumentCaptor.forClass(Exam.class);
        verify(exams).save(saved.capture());
        Exam exam = saved.getValue();
        assertTrue(exam.isShuffleParts());
        assertEquals("grammar", exam.getParts().get(0).getShuffleGroup());
        assertFalse(exam.getParts().get(0).isFixedPosition());
        when(exams.findByIdWithParts(id)).thenReturn(Optional.of(exam));
        var preview = service.getExamPreview(id);
        assertTrue(preview.isShuffleParts());
        assertEquals("grammar", preview.getParts().get(0).getShuffleGroup());
        assertEquals(1, preview.getParts().get(0).getOrderIndex());
        assertEquals(2, preview.getParts().get(1).getOrderIndex());
    }

    @Test
    void previewReturnsTranslateOnlyForReadingParts() {
        ExamRepository exams = mock(ExamRepository.class);
        ExamService service = new ExamService(exams, mock(ExamPartRepository.class),
                mock(VideoRepository.class), new ExamImportValidator(), mapper,
                mock(CacheRegistry.class), mock(PlatformTransactionManager.class));
        UUID id = UUID.randomUUID();
        Exam exam = Exam.builder().id(id).title("Exam").examType(ExamType.GENERAL_EXAM).build();
        for (var type : java.util.List.of(com.ice.iceenglish.modules.exams.enums.PartType.READING_8,
                com.ice.iceenglish.modules.exams.enums.PartType.READING_10,
                com.ice.iceenglish.modules.exams.enums.PartType.GRAMMAR)) {
            exam.addPart(com.ice.iceenglish.modules.exams.entity.ExamPart.builder()
                    .partType(type).partName(type.name()).translate("Dich " + type).orderIndex(1).build());
        }
        when(exams.findByIdWithParts(id)).thenReturn(Optional.of(exam));
        var parts = service.getExamPreview(id).getParts();
        assertEquals("Dich READING_8", parts.get(0).getTranslate());
        assertEquals("Dich READING_10", parts.get(1).getTranslate());
        org.junit.jupiter.api.Assertions.assertNull(parts.get(2).getTranslate());
    }
}
