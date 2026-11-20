package com.ice.iceenglish.modules.guest;

import com.ice.iceenglish.config.SecurityConfig;
import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GuestController.class)
@Import(SecurityConfig.class)
class GuestSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private GuestService service;
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
    void guestListIsPublicButStudentApiStillRequiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listVideos()).thenReturn(List.of(new GuestService.GuestVideo(id, "Sample", "2.3", true)));
        mvc.perform(get("/api/v1/guest/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accessible").value(true))
                .andExpect(jsonPath("$[0].videoId").value(id.toString()))
                .andExpect(jsonPath("$[0].position").value("2.3"))
                .andExpect(jsonPath("$[0].videoUrl").doesNotExist());
        mvc.perform(get("/api/v1/videos")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsGuestAttemptHeader() throws Exception {
        mvc.perform(options("/api/v1/guest/exams")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Guest-Attempt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", "X-Guest-Attempt"));
    }

    @Test
    void guestExamCatalogIsPublicAndContainsOnlyDisplayMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listExams(com.ice.iceenglish.modules.exams.enums.ExamType.MOCK))
                .thenReturn(List.of(new GuestService.GuestExam(id, "Exam",
                        com.ice.iceenglish.modules.exams.enums.ExamType.MOCK, 50, 40, false)));

        mvc.perform(get("/api/v1/guest/exams").param("mode", "MOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].title").value("Exam"))
                .andExpect(jsonPath("$[0].mode").value("MOCK"))
                .andExpect(jsonPath("$[0].durationMinutes").value(50))
                .andExpect(jsonPath("$[0].totalQuestions").value(40))
                .andExpect(jsonPath("$[0].accessible").value(false))
                .andExpect(jsonPath("$[0].questions").doesNotExist())
                .andExpect(jsonPath("$[0].answers").doesNotExist());
    }
}
