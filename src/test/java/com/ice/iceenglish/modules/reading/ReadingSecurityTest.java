package com.ice.iceenglish.modules.reading;

import com.ice.iceenglish.config.SecurityConfig;
import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import com.ice.iceenglish.modules.reading.ReadingDtos.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ReadingController.class, ReadingAdminController.class})
@Import(SecurityConfig.class)
class ReadingSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ReadingService service;
    @MockitoBean private JwtAuthenticationFilter filter;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void filterChain() throws Exception {
        doAnswer(invocation -> {
            ServletRequest req = invocation.getArgument(0);
            ServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(filter).doFilter(any(), any(), any());
    }

    @Test
    void guestListIsPublicButStudentAndAdminRoutesAreProtected() throws Exception {
        when(service.list(false, true, null, null, null, null, 0, 12))
                .thenReturn(new Page(List.of(), 0, 12, 0, 0));
        mvc.perform(get("/api/v1/guest/reading-blogs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/v1/reading-blogs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reading-blogs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void studentCanReadButCannotManage() throws Exception {
        when(service.list(false, false, null, null, null, null, 0, 12))
                .thenReturn(new Page(List.of(), 0, 12, 0, 0));
        mvc.perform(get("/api/v1/reading-blogs")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/reading-blogs/{id}/publish", id))
                .andExpect(status().isForbidden());
        verify(service, never()).publish(any());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminCanPublishAndSetGuestAccess() throws Exception {
        mvc.perform(patch("/api/v1/admin/reading-blogs/{id}/publish", id))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/admin/reading-blogs/{id}/guest-access", id)
                        .contentType("application/json").content("{\"guestAccessible\":true}"))
                .andExpect(status().isNoContent());
        verify(service).publish(id);
        verify(service).guestAccess(eq(id), argThat(request -> Boolean.TRUE.equals(request.guestAccessible())));
    }

    @Test
    void answerUsesGuestPermissionAndExpectedJsonShape() throws Exception {
        UUID question = UUID.randomUUID();
        when(service.answer(eq(id), eq(question), any(), eq(true)))
                .thenReturn(new AnswerResult(question, "C", true, "correct", "explanation", List.of("p1")));
        mvc.perform(post("/api/v1/guest/reading-blogs/{id}/questions/{question}/answer", id, question)
                        .contentType("application/json").content("{\"revision\":1,\"selectedOption\":\"C\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isCorrect").value(true))
                .andExpect(jsonPath("$.evidenceBlockIds[0]").value("p1"));
    }
}
