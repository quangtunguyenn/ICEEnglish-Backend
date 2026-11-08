package com.ice.iceenglish.modules.exams.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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
}
