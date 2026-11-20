package com.ice.iceenglish.modules.exams.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamImportDtoTest {

    @Test
    void acceptsShufflePartsFromExamMetadata() throws Exception {
        String json = """
                {
                  "exam": {
                    "title": "Mock exam",
                    "exam_type": "MOCK",
                    "duration_minutes": 50,
                    "shuffle_parts": true
                  },
                  "parts": []
                }
                """;

        ExamImportDto dto = JsonMapper.builder().build().readValue(json, ExamImportDto.class);

        assertTrue(dto.getExam().getShuffleParts());
    }

    @Test
    void acceptsMultipleRelatedVideoIdsAndLegacySingleId() throws Exception {
        UUID legacyId = UUID.randomUUID();
        UUID legacyArrayFirst = UUID.randomUUID();
        UUID legacyArraySecond = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String json = """
                {
                  "parts": [{
                    "questions": [
                      {"related_video_id": "%s"},
                      {"related_video_id": ["%s", "%s"]},
                      {"related_video_ids": ["%s", "%s"]}
                    ]
                  }]
                }
                """.formatted(legacyId, legacyArrayFirst, legacyArraySecond, firstId, secondId);

        ExamImportDto dto = JsonMapper.builder().build().readValue(json, ExamImportDto.class);
        var questions = dto.getParts().get(0).getQuestions();

        assertEquals(List.of(legacyId), questions.get(0).getRelatedVideoId());
        assertEquals(List.of(legacyArrayFirst, legacyArraySecond), questions.get(1).getRelatedVideoId());
        assertEquals(List.of(firstId, secondId), questions.get(2).getRelatedVideoIds());
    }
}
