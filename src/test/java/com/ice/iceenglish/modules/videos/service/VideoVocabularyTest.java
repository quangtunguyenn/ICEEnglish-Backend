package com.ice.iceenglish.modules.videos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoVocabularyTest {
    private final VideoRepository videos = mock(VideoRepository.class);
    private final ExamRepository exams = mock(ExamRepository.class);
    private final VocabularyRepository vocabularies = mock(VocabularyRepository.class);
    private final CacheRegistry cache = mock(CacheRegistry.class);
    private VideoService service;
    private String sample;

    @BeforeEach
    void setUp() throws Exception {
        when(cache.create(anyLong())).thenAnswer(invocation -> Caffeine.newBuilder().build());
        lenient().when(cache.getOrLoad(any(Cache.class), any(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(2);
            return supplier.get();
        });
        service = new VideoService(videos, exams, mock(ExamPartRepository.class), vocabularies,
                new ObjectMapper(), cache);
        sample = Files.readString(Path.of("samples", "learning-bundle-sample.json"));
    }

    @Test
    void importsUnicodeAndLegacyItemsAndUsesSameDataInBothReads() {
        UUID id = UUID.randomUUID();
        when(videos.save(any())).thenAnswer(inv -> {
            Video video = inv.getArgument(0);
            video.setId(id);
            return video;
        });
        when(exams.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.createLearningBundle(file(sample), "teacher");
        ArgumentCaptor<Iterable<Vocabulary>> captured = ArgumentCaptor.forClass(Iterable.class);
        verify(vocabularies).saveAll(captured.capture());
        List<Vocabulary> saved = (List<Vocabulary>) captured.getValue();
        assertEquals("/ˌkɒn.vəˈseɪ.ʃən/", saved.get(0).getPhonetic());
        assertEquals("We had a conversation about school.", saved.get(0).getExampleSentence());
        assertEquals("Chúng tôi đã trò chuyện về trường học.", saved.get(0).getExampleSentenceTranslate());
        assertNull(saved.get(1).getPhonetic());
        assertNull(saved.get(1).getExampleSentence());
        assertNull(saved.get(1).getExampleSentenceTranslate());
        verify(cache).evictAllAfterCommit();

        when(videos.findById(id)).thenReturn(Optional.of(Video.builder().id(id).title("Video").build()));
        when(vocabularies.findByVideoIdOrderByCreatedAtAsc(id)).thenReturn(saved);
        var direct = service.getVocabularies(id);
        var embedded = service.getLearningContent(id).getVocabularies();
        assertEquals(direct, embedded);
        assertEquals(saved.get(0).getExampleSentenceTranslate(), direct.get(0).getExampleSentenceTranslate());
        assertNull(direct.get(1).getExampleSentenceTranslate());
    }

    @Test
    void rejectsNonStringTypesAndTranslationWithoutExampleBeforeWriting() {
        for (String invalid : List.of(
                sample.replace("\"phonetic\": \"/ˌkɒn.vəˈseɪ.ʃən/\"", "\"phonetic\": 123"),
                sample.replace("\"example_sentence\": \"We had a conversation about school.\"", "\"example_sentence\": false"),
                sample.replace("\"example_sentence_translate\": \"Chúng tôi đã trò chuyện về trường học.\"", "\"example_sentence_translate\": {}"),
                sample.replace("\"example_sentence\": \"We had a conversation about school.\"", "\"example_sentence\": null"))) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.createLearningBundle(file(invalid), "teacher"));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(videos, exams, vocabularies);
    }

    @Test
    void validatesLimitsAndAcceptsNullsAndBoundaryValues() {
        String base = sample.replace("/ˌkɒn.vəˈseɪ.ʃən/", "x".repeat(250))
                .replace("We had a conversation about school.", "y".repeat(5000))
                .replace("Chúng tôi đã trò chuyện về trường học.", "z".repeat(5000));
        when(videos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(exams.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> service.createLearningBundle(file(base), "teacher"));
        for (String invalid : List.of(
                base.replace("x".repeat(250), "x".repeat(251)),
                base.replace("y".repeat(5000), "y".repeat(5001)),
                base.replace("z".repeat(5000), "z".repeat(5001)))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.createLearningBundle(file(invalid), "teacher")).getStatusCode().value());
        }
        String empty = sample.replace("\"phonetic\": \"/ˌkɒn.vəˈseɪ.ʃən/\"", "\"phonetic\": null")
                .replace("\"example_sentence\": \"We had a conversation about school.\"", "\"example_sentence\": \"  \"")
                .replace("\"example_sentence_translate\": \"Chúng tôi đã trò chuyện về trường học.\"", "\"example_sentence_translate\": null");
        assertDoesNotThrow(() -> service.createLearningBundle(file(empty), "teacher"));
    }

    private static MockMultipartFile file(String json) {
        return new MockMultipartFile("file", "bundle.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
