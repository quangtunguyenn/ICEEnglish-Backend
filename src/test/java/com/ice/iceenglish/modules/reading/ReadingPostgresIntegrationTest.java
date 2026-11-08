package com.ice.iceenglish.modules.reading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.modules.reading.ReadingDtos.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReadingPostgresIntegrationTest {
    private static String schema;
    private static JdbcTemplate root;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static ReadingService service;

    @BeforeAll
    static void createIsolatedSchema() {
        String url = System.getenv("READING_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Set READING_TEST_DB_URL to run PostgreSQL integration tests.");
        String user = System.getenv("READING_TEST_DB_USER");
        String password = System.getenv("READING_TEST_DB_PASSWORD");
        DriverManagerDataSource base = new DriverManagerDataSource(url, user, password);
        root = new JdbcTemplate(base);
        schema = "reading_test_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE SCHEMA " + schema);
        String separator = url.contains("?") ? "&" : "?";
        DriverManagerDataSource isolated = new DriverManagerDataSource(url + separator + "currentSchema=" + schema,
                user, password);
        jdbc = new JdbcTemplate(isolated);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(isolated));
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V6__Create_reading_library.sql")).execute(isolated);
        service = new ReadingService(jdbc, new ObjectMapper(), new ReadingImportValidator());
    }

    @AfterAll
    static void removeIsolatedSchema() {
        if (root != null && schema != null) root.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void importPublishReadAnswerAndRevokeWithoutSavingAttempts() throws Exception {
        byte[] file = Files.readAllBytes(Path.of("samples", "reading-blog-sample.json"));
        MockMultipartFile upload = new MockMultipartFile("file", "sample.json", "application/json", file);
        ImportResult imported = transaction.execute(status -> service.importFile(upload, "admin"));
        UUID id = imported.id();
        assertEquals("DRAFT", imported.status());
        assertThrows(ResponseStatusException.class, () -> service.detail(id, false));
        assertEquals(1, service.adminDetail(id).revision());
        transaction.executeWithoutResult(status -> service.publish(id));
        Detail detail = service.detail(id, true);
        assertEquals(2, detail.passageBlocks().size());
        assertEquals(2, detail.questions().size());
        assertEquals(1, service.vocabularies(id, true).size());
        UUID questionId = detail.questions().get(1).id();
        AnswerResult result = service.answer(id, questionId, new AnswerRequest(1, "C"), true);
        assertTrue(result.isCorrect());
        assertEquals("p2", result.evidenceBlockIds().get(0));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.answer(id, questionId, new AnswerRequest(2, "C"), true)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.answer(id, UUID.randomUUID(), new AnswerRequest(1, "C"), true)).getStatusCode().value());
        assertEquals(1, service.list(false, true, "environment", "ARTICLE", "Garden", null, 0, 12).items().size());
        transaction.executeWithoutResult(status -> service.guestAccess(id, new GuestAccessRequest(false)));
        assertFalse(service.list(false, true, null, null, null, null, 0, 12).items().get(0).accessible());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.detail(id, true))
                .getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.answer(id, questionId, new AnswerRequest(1, "C"), true)).getStatusCode().value());
        assertNotNull(service.detail(id, false));
        transaction.executeWithoutResult(status -> service.archive(id));
        assertTrue(service.list(false, false, null, null, null, null, 0, 12).items().isEmpty());
        int before = jdbc.queryForObject("SELECT count(*) FROM reading_blogs", Integer.class);
        String conflicting = new String(file, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"name\": \"Environment\"", "\"name\": \"A different name\"");
        MockMultipartFile bad = new MockMultipartFile("file", "conflict.json", "application/json",
                conflicting.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> transaction.execute(status -> service.importFile(bad, "admin"))).getStatusCode().value());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM reading_blogs", Integer.class));
        String failsAfterTopicInsert = new String(file, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"slug\": \"environment\"", "\"slug\": \"new-topic\"");
        MockMultipartFile invalidDb = new MockMultipartFile("file", "rollback.json", "application/json",
                failsAfterTopicInsert.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> transaction.execute(status -> service.importFile(invalidDb, "a".repeat(101))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reading_topics WHERE slug='new-topic'", Integer.class));
        assertEquals(5, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema=current_schema() AND table_type='BASE TABLE'
                """, Integer.class));
    }
}
