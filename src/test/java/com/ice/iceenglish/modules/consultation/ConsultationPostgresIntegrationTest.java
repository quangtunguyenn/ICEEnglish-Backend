package com.ice.iceenglish.modules.consultation;

import com.ice.iceenglish.modules.consultation.ConsultationDtos.CreateRequest;
import com.ice.iceenglish.modules.consultation.ConsultationDtos.StatusRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CONSULTATION_TEST_DB_URL", matches = ".+")
class ConsultationPostgresIntegrationTest {
    private static final String SCHEMA = "consultation_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate root;
    @Autowired private ConsultationService service;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = System.getenv("CONSULTATION_TEST_DB_URL");
        String user = System.getenv("CONSULTATION_TEST_DB_USER");
        String password = System.getenv("CONSULTATION_TEST_DB_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        root.execute("CREATE SCHEMA " + SCHEMA);
        String isolated = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        var dataSource = new DriverManagerDataSource(isolated, user, password);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V10__Create_consultation_requests.sql")).execute(dataSource);
        registry.add("spring.datasource.url", () -> isolated);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    @AfterAll
    static void cleanup() {
        if (root != null) root.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void migrationAndJpaSupportCreateUpdateAndAllFilters() {
        var created = service.create(new CreateRequest("An", BigDecimal.valueOf(2008),
                new BigDecimal("8.5555"), "0901234567"));
        assertNotNull(created.id());
        assertEquals(ConsultationStatus.NOT_DONE, service.list(null, null).get(0).status());
        assertEquals(new BigDecimal("8.5555"), service.list(null, null).get(0).targetGrade());
        service.updateStatus(created.id(), new StatusRequest(ConsultationStatus.DONE));
        assertEquals(ConsultationStatus.DONE, service.list(ConsultationStatus.DONE, null).get(0).status());
        assertTrue(service.list(ConsultationStatus.NOT_DONE, null).isEmpty());

        jdbc.update("UPDATE consultation_requests SET date_request = ? WHERE id = ?",
                java.sql.Timestamp.valueOf("2026-01-02 00:00:00"), created.id());
        UUID nextDay = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO consultation_requests
                    (id, student_fullname, bod, target_grade, phone_number_request, date_request, status)
                VALUES (?, 'B', 2009, 7.5, '091', TIMESTAMP '2026-01-03 00:00:00', 'NOT_DONE')
                """, nextDay);
        LocalDate day = LocalDate.of(2026, 1, 2);
        assertEquals(created.id(), service.list(null, day).get(0).id());
        assertEquals(1, service.list(null, day).size());
        assertEquals(created.id(), service.list(ConsultationStatus.DONE, day).get(0).id());
        assertTrue(service.list(ConsultationStatus.NOT_DONE, day).isEmpty());
    }
}
