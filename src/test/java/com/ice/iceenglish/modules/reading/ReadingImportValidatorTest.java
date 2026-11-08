package com.ice.iceenglish.modules.reading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.modules.reading.ReadingDtos.ImportFile;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReadingImportValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReadingImportValidator validator = new ReadingImportValidator();

    @Test
    void sampleFileIsValidAndContainsBothSkills() throws Exception {
        ImportFile file = mapper.readValue(Files.readString(Path.of("samples", "reading-blog-sample.json")),
                ImportFile.class);
        assertDoesNotThrow(() -> validator.validate(file));
        assertEquals("SKIMMING", file.questions().get(0).skillType());
        assertEquals("SCANNING", file.questions().get(1).skillType());
    }

    @Test
    void detectsDuplicateCorrectAnswersAndUnknownEvidence() throws Exception {
        String input = Files.readString(Path.of("samples", "reading-blog-sample.json"));
        String duplicateCorrect = input.replace("\"option_key\": \"B\", \"content\": \"Every school has a large farm.\", \"is_correct\": false",
                "\"option_key\": \"B\", \"content\": \"Every school has a large farm.\", \"is_correct\": true");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> validator.validate(mapper.readValue(duplicateCorrect, ImportFile.class)));
        assertTrue(e.getReason().contains("questions[0].answers"));
        String invalidEvidence = input.replace("\"evidence_block_ids\": [\"p2\"]",
                "\"evidence_block_ids\": [\"missing\"]");
        e = assertThrows(ResponseStatusException.class,
                () -> validator.validate(mapper.readValue(invalidEvidence, ImportFile.class)));
        assertTrue(e.getReason().contains("questions[1].evidence_block_ids"));
    }

    @Test
    void rejectsDangerousUrlAndMissingDifficulty() throws Exception {
        String input = Files.readString(Path.of("samples", "reading-blog-sample.json"));
        String invalidUrl = input.replace("\"cover_image_url\": null", "\"cover_image_url\": \"javascript:alert(1)\"");
        assertThrows(ResponseStatusException.class,
                () -> validator.validate(mapper.readValue(invalidUrl, ImportFile.class)));
        String missing = input.replace("\"difficulty\": \"MEDIUM\"", "\"difficulty\": null");
        assertThrows(ResponseStatusException.class,
                () -> validator.validate(mapper.readValue(missing, ImportFile.class)));
    }
}
