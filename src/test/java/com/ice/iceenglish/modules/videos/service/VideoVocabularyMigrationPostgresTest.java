package com.ice.iceenglish.modules.videos.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VideoVocabularyMigrationPostgresTest {
    private static String schema;
    private static JdbcTemplate root;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("VIDEO_VOCAB_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Set VIDEO_VOCAB_TEST_DB_URL for PostgreSQL migration test.");
        String user = System.getenv("VIDEO_VOCAB_TEST_DB_USER");
        String password = System.getenv("VIDEO_VOCAB_TEST_DB_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "vocab_test_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE SCHEMA " + schema);
        String separator = url.contains("?") ? "&" : "?";
        DriverManagerDataSource isolated = new DriverManagerDataSource(url + separator + "currentSchema=" + schema,
                user, password);
        jdbc = new JdbcTemplate(isolated);
        jdbc.execute("CREATE TABLE vocabularies (id uuid PRIMARY KEY, word varchar(100) NOT NULL, meaning text NOT NULL)");
        jdbc.update("INSERT INTO vocabularies(id,word,meaning) VALUES (?,?,?)", UUID.randomUUID(), "old", "cũ");
        var migration = new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V7__Add_video_vocabulary_details.sql"));
        migration.execute(isolated);
        migration.execute(isolated);
    }

    @AfterAll
    static void cleanup() {
        if (root != null && schema != null) root.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void migrationPreservesLegacyRowsAndPersistsUnicode() {
        var old = jdbc.queryForMap("SELECT * FROM vocabularies WHERE word='old'");
        assertEquals("cũ", old.get("meaning"));
        assertNull(old.get("phonetic"));
        assertNull(old.get("example_sentence"));
        assertNull(old.get("example_sentence_translate"));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO vocabularies(id,word,meaning,phonetic,example_sentence,example_sentence_translate)
                VALUES (?,?,?,?,?,?)
                """, id, "garden", "khu vườn", "/ˈɡɑːr.dən/", "A garden 🌱.", "Một khu vườn 🌱.");
        var row = jdbc.queryForMap("SELECT * FROM vocabularies WHERE id=?", id);
        assertEquals("/ˈɡɑːr.dən/", row.get("phonetic"));
        assertEquals("A garden 🌱.", row.get("example_sentence"));
        assertEquals("Một khu vườn 🌱.", row.get("example_sentence_translate"));
    }
}
