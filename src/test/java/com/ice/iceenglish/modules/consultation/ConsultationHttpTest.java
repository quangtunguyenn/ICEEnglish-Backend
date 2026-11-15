package com.ice.iceenglish.modules.consultation;

import com.ice.iceenglish.config.SecurityConfig;
import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConsultationController.class)
@Import({SecurityConfig.class, ConsultationService.class})
class ConsultationHttpTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ConsultationRequestRepository repository;
    @MockitoBean private JwtAuthenticationFilter filter;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(call -> {
            FilterChain chain = call.getArgument(2);
            chain.doFilter(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T17:30:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(repository.save(any())).thenAnswer(call -> {
            ConsultationRequest request = call.getArgument(0);
            request.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return request;
        });
    }

    @Test
    void anonymousCanCreateButCannotReadOrUpdate() throws Exception {
        mvc.perform(post("/api/v1/consultation-requests").contentType("application/json")
                .content("""
                        {"studentFullname":"An","bod":2008,"targetGrade":8.5,"phoneNumberRequest":"0901234567"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.targetGrade").value(8.5))
                .andExpect(jsonPath("$.dateRequest").value("2026-01-02T00:30:00"))
                .andExpect(jsonPath("$.status").value("NOT_DONE"));
        mvc.perform(get("/api/v1/consultation-requests")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/consultation-requests/{id}/status", UUID.randomUUID())
                .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void studentCannotReadOrUpdate() throws Exception {
        mvc.perform(get("/api/v1/consultation-requests")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/consultation-requests/{id}/status", UUID.randomUUID())
                .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_TEACHER")
    void staffCanFilterAndBadInputsReturn400() throws Exception {
        mvc.perform(get("/api/v1/consultation-requests").param("status", "DONE").param("date", "2026-01-02"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/consultation-requests").param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/consultation-requests").param("date", "2026-02-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/consultation-requests/{id}/status", UUID.randomUUID())
                .contentType("application/json").content("{\"status\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("status is required"));
        mvc.perform(post("/api/v1/consultation-requests").contentType("application/json")
                .content("{\"studentFullname\":\"An\",\"bod\":2008,\"targetGrade\":11,\"phoneNumberRequest\":\"090\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void staffCanUpdateAndUnknownRequestReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        ConsultationRequest request = new ConsultationRequest();
        request.setId(id);
        request.setStatus(ConsultationStatus.NOT_DONE);
        when(repository.findById(id)).thenReturn(Optional.of(request));
        mvc.perform(patch("/api/v1/consultation-requests/{id}/status", id)
                .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(patch("/api/v1/consultation-requests/{id}/status", UUID.randomUUID())
                .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fractionalBirthYearIsInvalid() throws Exception {
        mvc.perform(post("/api/v1/consultation-requests").contentType("application/json")
                .content("{\"studentFullname\":\"An\",\"bod\":2008.5,\"targetGrade\":8.5,\"phoneNumberRequest\":\"090\"}"))
                .andExpect(status().isBadRequest());
    }
}
