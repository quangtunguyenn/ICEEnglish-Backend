package com.ice.iceenglish.modules.videos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.submissions.repository.PartTimeLogRepository;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.dto.VideoTitleResponse;
import com.ice.iceenglish.modules.videos.enums.VideoType;
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
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final VocabularyRepository vocabularies = mock(VocabularyRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubDetailRepository subDetails = mock(SubDetailRepository.class);
    private final PartTimeLogRepository partTimeLogs = mock(PartTimeLogRepository.class);
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
        service = new VideoService(videos, exams, mock(ExamPartRepository.class), questions,
                vocabularies, submissions, subDetails, partTimeLogs, new ObjectMapper(), cache);
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
        ArgumentCaptor<Video> capturedVideo = ArgumentCaptor.forClass(Video.class);
        verify(videos).save(capturedVideo.capture());
        assertEquals(VideoType.LECTURE, capturedVideo.getValue().getVideoType());
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

    @Test
    void nonLectureBundlesMayOmitHomeworkAndVocabulary() {
        when(videos.save(any())).thenAnswer(inv -> {
            Video video = inv.getArgument(0);
            video.setId(UUID.randomUUID());
            return video;
        });

        for (String type : List.of("practice", "exam_tips")) {
            String json = """
                    {
                      "video": {
                        "type": "%s",
                        "title": "Standalone video",
                        "videoUrl": "https://example.com/video"
                      }
                    }
                    """.formatted(type);

            var response = service.createLearningBundle(file(json), "teacher");

            assertNull(response.getHomeworkId());
            assertEquals(0, response.getVocabularyCount());
        }

        verify(exams, never()).save(any());
        verifyNoInteractions(vocabularies);
    }

    @Test
    void bundlePositionIsOptionalAndNumericComponentsAreStored() {
        when(videos.save(any())).thenAnswer(inv -> {
            Video video = inv.getArgument(0);
            video.setId(UUID.randomUUID());
            return video;
        });
        String json = """
                {"video":{"type":"practice","title":"Positioned video",
                  "position":"2.10","videoUrl":"https://example.com/video"}}
                """;

        service.createLearningBundle(file(json), "teacher");

        ArgumentCaptor<Video> captured = ArgumentCaptor.forClass(Video.class);
        verify(videos).save(captured.capture());
        assertEquals("2.10", captured.getValue().getPosition());
        assertEquals(2, captured.getValue().getPositionChapter());
        assertEquals(10, captured.getValue().getPositionLesson());
    }

    @Test
    void adminAndTeacherPositionUpdateValidatesConflictsAndCanClear() {
        UUID id = UUID.randomUUID();
        Video video = Video.builder().id(id).title("Current video").build();
        when(videos.findById(id)).thenReturn(Optional.of(video));
        when(videos.findByPosition(anyString())).thenReturn(Optional.empty());

        service.updatePosition(id, "2.10");

        assertEquals("2.10", video.getPosition());
        assertEquals(2, video.getPositionChapter());
        assertEquals(10, video.getPositionLesson());
        verify(cache).evictAllAfterCommit();

        for (String invalid : List.of("1.2.3", "1.", "chapter", "2147483648")) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.updatePosition(id, invalid));
            assertEquals(400, error.getStatusCode().value());
        }

        UUID otherId = UUID.randomUUID();
        when(videos.findByPosition("1.2")).thenReturn(
                Optional.of(Video.builder().id(otherId).title("Existing video").build()));
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service.updatePosition(id, "1.2"));
        assertEquals(409, conflict.getStatusCode().value());
        assertTrue(conflict.getReason().contains("Existing video"));
        assertEquals("2.10", video.getPosition());

        service.updatePosition(id, " ");
        assertNull(video.getPosition());
        assertNull(video.getPositionChapter());
        assertNull(video.getPositionLesson());
        verify(cache, times(2)).evictAllAfterCommit();
    }

    @Test
    void deletingVideoRemovesItsLearningBundleAndSubmissionHistory() {
        UUID videoId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Video video = Video.builder().id(videoId).build();
        Exam homework = Exam.builder().id(examId).build();
        when(videos.findById(videoId)).thenReturn(Optional.of(video));
        when(exams.findByVideoIdWithParts(videoId)).thenReturn(List.of(homework));

        service.delete(videoId);

        var order = inOrder(
                videos, exams, subDetails, partTimeLogs, submissions,
                questions, vocabularies, cache);
        order.verify(videos).findById(videoId);
        order.verify(exams).findByVideoIdWithParts(videoId);
        order.verify(subDetails).deleteBySubmissionExamIds(List.of(examId));
        order.verify(partTimeLogs).deleteBySubmissionExamIds(List.of(examId));
        order.verify(submissions).deleteByExamIds(List.of(examId));
        order.verify(questions).clearRelatedVideoReference(videoId);
        order.verify(vocabularies).deleteByVideoId(videoId);
        order.verify(exams).clearVideoReference(videoId);
        order.verify(exams).deleteAll(List.of(homework));
        order.verify(videos).delete(video);
        order.verify(cache).evictAllAfterCommit();
    }

    @Test
    void everyVideoListResponseIncludesPosition() {
        Video positioned = Video.builder().id(UUID.randomUUID()).title("Positioned")
                .position("1.2").guestAccessible(true).build();
        Video unpositioned = Video.builder().id(UUID.randomUUID()).title("Unpositioned").build();
        when(videos.findAllInDisplayOrder()).thenReturn(List.of(positioned, unpositioned));

        List<VideoTitleResponse> titles = service.getAllTitles();
        assertEquals("1.2", titles.get(0).getPosition());
        assertNull(titles.get(1).getPosition());
        List<com.ice.iceenglish.modules.videos.dto.VideoGuestTitleResponse> guestTitles =
                service.getAllGuestTitles();
        assertEquals("1.2", guestTitles.get(0).position());
        assertNull(guestTitles.get(1).position());
        List<VideoResponseDto> videos = service.getAll(null);
        assertEquals("1.2", videos.get(0).getPosition());
        assertNull(videos.get(1).getPosition());
    }

    @Test
    void lectureBundlesStillRequireHomeworkAndVocabularyAndRejectUnknownTypes() {
        String noHomework = """
                {"video":{"title":"Lecture","videoUrl":"https://example.com/video"},
                 "vocabularies":[{"word":"word","meaning":"meaning"}]}
                """;
        String noVocabulary = """
                {"video":{"title":"Lecture","videoUrl":"https://example.com/video"},
                 "homework":{"title":"Homework","questions":[
                   {"orderIndex":1,"content":"Question","options":{"A":"a","B":"b","C":"c","D":"d"},
                    "correctOption":"A"}
                 ]}}
                """;
        String unknownType = """
                {"video":{"type":"other","title":"Video","videoUrl":"https://example.com/video"}}
                """;

        for (String json : List.of(noHomework, noVocabulary, unknownType)) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.createLearningBundle(file(json), "teacher"))
                    .getStatusCode().value());
        }

        verifyNoInteractions(videos, exams, vocabularies);
    }

    @Test
    void guestAccessCanBeToggledAndControlsGuestCatalog() {
        UUID id = UUID.randomUUID();
        Video sample = Video.builder().id(id).title("Sample").build();
        Video locked = Video.builder().id(UUID.randomUUID()).title("Locked").build();
        when(videos.findById(id)).thenReturn(Optional.of(sample));
        when(videos.findAllInDisplayOrder()).thenReturn(List.of(sample, locked));

        assertFalse(service.getAllGuestTitles().get(0).accessible());
        service.guestAccess(id, true);
        assertTrue(sample.isGuestAccessible());
        assertDoesNotThrow(() -> service.requireGuestAccess(id));
        assertEquals(List.of(true, false),
                service.getAllGuestTitles().stream().map(item -> item.accessible()).toList());

        service.guestAccess(id, false);
        assertFalse(sample.isGuestAccessible());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireGuestAccess(id));
        assertEquals(403, error.getStatusCode().value());
        verify(cache, times(2)).evictAllAfterCommit();
    }

    private static MockMultipartFile file(String json) {
        return new MockMultipartFile("file", "bundle.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
