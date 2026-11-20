package com.ice.iceenglish.config;

import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = ManagementMetricsIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
                "app.cors.allowed-origins=http://localhost:3000"
        })
@ActiveProfiles("prod")
class ManagementMetricsIntegrationTest {
    private static final int MANAGEMENT_PORT = availablePort();
    @LocalServerPort private int serverPort;
    @MockitoBean private JwtAuthenticationFilter filter;

    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @BeforeEach
    void continueFilterChain() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(filter).doFilter(any(), any(), any());
    }

    @Test
    void prometheusScrapeWorksOnlyOnManagementPort() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        assertEquals(200, get(client, serverPort, "/api/v1/guest/ping").statusCode());
        assertEquals(401, get(client, serverPort, "/actuator/prometheus").statusCode());
        assertEquals(404, get(client, serverPort, "/v3/api-docs").statusCode());
        assertEquals(404, get(client, serverPort, "/swagger-ui/index.html").statusCode());

        HttpResponse<String> metrics = get(client, MANAGEMENT_PORT, "/actuator/prometheus");
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("http_server_requests_seconds_count"));
        assertTrue(metrics.body().contains("http_server_requests_seconds_bucket"));
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
        assertEquals(200, get(client, MANAGEMENT_PORT, "/actuator/health").statusCode());
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot allocate a management test port", e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, PingController.class})
    static class TestApplication {
    }

    @RestController
    static class PingController {
        @GetMapping("/api/v1/guest/ping")
        String ping() {
            return "ok";
        }
    }
}
