package com.ice.iceenglish.modules.videos.service;

import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.guest.GuestService;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VIDEO_VOCAB_TEST_DB_URL", matches = ".+")
class VideoVocabularyPostgresIntegrationTest {
    private static final String SCHEMA = "vocab_app_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate root;
    @Autowired private VideoService service;
    @Autowired private VideoRepository videos;
    @Autowired private VocabularyRepository vocabularies;
    @Autowired private ExamRepository exams;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = System.getenv("VIDEO_VOCAB_TEST_DB_URL");
        String user = System.getenv("VIDEO_VOCAB_TEST_DB_USER");
        String password = System.getenv("VIDEO_VOCAB_TEST_DB_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        root.execute("CREATE SCHEMA " + SCHEMA);
        String isolated = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        registry.add("spring.datasource.url", () -> isolated);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    @AfterAll
    static void cleanup() {
        if (root != null) root.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void importedValuesRoundTripAndInvalidHomeworkRollsBackVideo() throws Exception {
        String json = Files.readString(Path.of("samples", "learning-bundle-sample.json"));
        var response = service.createLearningBundle(file(json), "teacher");
        var direct = service.getVocabularies(response.getVideoId());
        var embedded = service.getLearningContent(response.getVideoId()).getVocabularies();
        assertEquals(direct, embedded);
        var item = direct.stream().filter(v -> "conversation".equals(v.getWord())).findFirst().orElseThrow();
        assertEquals("/ˌkɒn.vəˈseɪ.ʃən/", item.getPhonetic());
        assertEquals("We had a conversation about school.", item.getExampleSentence());
        assertEquals("Chúng tôi đã trò chuyện về trường học.", item.getExampleSentenceTranslate());
        service.guestAccess(response.getVideoId(), true);
        GuestService guest = new GuestService(30,
                mock(com.ice.iceenglish.modules.exams.repository.ExamRepository.class),
                mock(ExamSnapshotService.class), mock(ExamService.class), service,
                mock(SubmissionService.class), Clock.systemUTC());
        assertEquals(direct, guest.vocabularies(response.getVideoId()));
        assertEquals(direct, guest.learningContent(response.getVideoId()).getVocabularies());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> guest.vocabularies(UUID.randomUUID())).getStatusCode().value());
        Vocabulary persisted = vocabularies.findById(item.getId()).orElseThrow();
        assertEquals(item.getPhonetic(), persisted.getPhonetic());
        assertNull(direct.stream().filter(v -> "ambition".equals(v.getWord()))
                .findFirst().orElseThrow().getExampleSentence());
        long count = videos.count();
        long vocabularyCount = vocabularies.count();
        long examCount = exams.count();
        String invalidHomework = json.replace("\"correctOpt\": \"D\"", "\"correctOpt\": \"Z\"");
        assertThrows(ResponseStatusException.class, () -> service.createLearningBundle(file(invalidHomework), "teacher"));
        assertEquals(count, videos.count());
        assertEquals(vocabularyCount, vocabularies.count());
        assertEquals(examCount, exams.count());
    }

    @Test
    void videoListsSortPositionComponentsNumericallyAndPlaceUnpositionedLast() {
        videos.saveAllAndFlush(List.of(
                video("Chapter 2", "2", 2, 0),
                video("Lesson 1.10", "1.10", 1, 10),
                video("Lesson 1.2", "1.2", 1, 2),
                video("Chapter 1", "1", 1, 0),
                video("No position", null, null, null)));

        List<Video> listed = service.getAll(null).stream()
                .map(item -> videos.findById(item.getId()).orElseThrow())
                .toList();

        assertEquals(List.of("Chapter 1", "Lesson 1.2", "Lesson 1.10", "Chapter 2"),
                listed.subList(0, 4).stream().map(Video::getTitle).toList());
        assertTrue(listed.subList(4, listed.size()).stream().allMatch(v -> v.getPosition() == null));
    }

    private static Video video(String title, String position, Integer chapter, Integer lesson) {
        return Video.builder().title(title).position(position).positionChapter(chapter)
                .positionLesson(lesson).videoUrl("https://example.com/" + title.replace(' ', '-')).build();
    }

    private static MockMultipartFile file(String json) {
        return new MockMultipartFile("file", "bundle.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
