package com.ice.iceenglish.modules.users;

import com.ice.iceenglish.modules.users.dto.LoginRequest;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import com.ice.iceenglish.modules.users.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STUDENT_TEST_DB_URL", matches = ".+")
class StudentManagementPostgresIntegrationTest {
    private static final String SCHEMA = "student_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate root;
    private static DriverManagerDataSource isolatedDataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthService auth;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = System.getenv("STUDENT_TEST_DB_URL");
        String user = System.getenv("STUDENT_TEST_DB_USER");
        String password = System.getenv("STUDENT_TEST_DB_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        root.execute("CREATE SCHEMA " + SCHEMA);
        String isolated = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        isolatedDataSource = new DriverManagerDataSource(isolated, user, password);
        JdbcTemplate setup = new JdbcTemplate(isolatedDataSource);
        setup.execute("""
                CREATE TABLE users (
                    id uuid PRIMARY KEY, username varchar(100) NOT NULL UNIQUE,
                    password_hash varchar(255) NOT NULL, role varchar(20) NOT NULL,
                    full_name varchar(150), student_irn varchar(50), password_mock varchar(100),
                    dob date, gender varchar(20), created_at timestamp
                )
                """);
        setup.update("INSERT INTO users (id, username, password_hash, role) VALUES (?, 'legacy', 'hash', 'STUDENT')",
                UUID.randomUUID());
        migrate();
        registry.add("spring.datasource.url", () -> isolated);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    private static void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V11__Add_user_active_status.sql")).execute(isolatedDataSource);
    }

    @AfterAll
    static void cleanup() {
        if (root != null) root.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void migrationAndAuthenticatedLifecycleWorkAgainstPostgres() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT is_active FROM users WHERE username = 'legacy'", Boolean.class));
        jdbc.update("UPDATE users SET is_active = false WHERE username = 'legacy'");
        migrate();
        assertFalse(jdbc.queryForObject("SELECT is_active FROM users WHERE username = 'legacy'", Boolean.class));

        User student = users.save(User.builder().username("student").passwordHash(encoder.encode("secret"))
                .fullName("Đặng Thị Ánh").role("STUDENT").build());
        User admin = users.save(User.builder().username("admin").passwordHash(encoder.encode("secret"))
                .role("ADMIN").build());
        String studentToken = login(student.getUsername());
        String adminToken = login(admin.getUsername());
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(true));
        mvc.perform(get("/api/v1/admin/students").header("Authorization", "Bearer " + adminToken)
                .param("isActive", "true").param("keyword", "dang thi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("student"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", admin.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", student.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"secret\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", student.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"isActive\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    private String login(String username) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword("secret");
        return auth.login(request);
    }
}
