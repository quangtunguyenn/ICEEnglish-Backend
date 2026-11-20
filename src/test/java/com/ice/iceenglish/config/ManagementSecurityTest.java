package com.ice.iceenglish.config;

import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagementSecurityTest.ManagementController.class)
@Import({SecurityConfig.class, ManagementSecurityTest.ManagementController.class})
@TestPropertySource(properties = "management.server.port=9091")
class ManagementSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtAuthenticationFilter filter;

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
    void onlyLocalConnectionsOnManagementPortCanAccessMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            request.setLocalPort(9091);
            return request;
        })).andExpect(status().isOk());

        mvc.perform(get("/actuator/prometheus").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            request.setLocalPort(8080);
            return request;
        })).andExpect(status().isUnauthorized());

        mvc.perform(get("/actuator/prometheus").header("X-Forwarded-For", "127.0.0.1")
                .with(user("admin").roles("ADMIN"))
                .with(request -> {
                    request.setRemoteAddr("192.0.2.1");
                    request.setLocalPort(9091);
                    return request;
                })).andExpect(status().isForbidden());
    }

    @RestController
    static class ManagementController {
        @GetMapping("/actuator/prometheus")
        String metrics() {
            return "metrics";
        }
    }
}
