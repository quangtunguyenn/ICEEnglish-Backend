package com.ice.iceenglish.modules.videos.controller;

import com.ice.iceenglish.config.SecurityConfig;
import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.guest.GuestController;
import com.ice.iceenglish.modules.guest.GuestService;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import com.ice.iceenglish.modules.videos.controller.VideoAdminController;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.exception.VideoPositionException;
import com.ice.iceenglish.modules.videos.service.VideoService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({VideoController.class, GuestController.class, VideoAdminController.class})
@Import({SecurityConfig.class, GuestService.class, VideoVocabularyHttpTest.TestClock.class})
class VideoVocabularyHttpTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private VideoService videos;
    @MockitoBean private ExamService exams;
    @MockitoBean private ExamSnapshotService snapshots;
    @MockitoBean private ExamRepository examRepository;
    @MockitoBean private SubmissionService submissions;
    @MockitoBean private JwtAuthenticationFilter filter;
    private final UUID videoId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    static class TestClock {
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(call -> {
            FilterChain chain = call.getArgument(2);
            chain.doFilter(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
        var vocab = VocabularyResponseDto.builder().id(UUID.randomUUID()).word("garden")
                .meaning("khu vườn").phonetic("/ˈɡɑːr.dən/")
                .exampleSentence("The garden is green.")
                .exampleSentenceTranslate("Khu vườn xanh.").build();
        var old = VocabularyResponseDto.builder().id(UUID.randomUUID()).word("old").meaning("cũ").build();
        when(videos.getVocabularies(videoId)).thenReturn(List.of(vocab, old));
        when(videos.getLearningContent(videoId)).thenReturn(VideoLearningContentResponse.builder()
                .videoId(videoId).title("Sample").vocabularies(List.of(vocab, old)).build());
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void studentEndpointsReturnTheNewFields() throws Exception {
        mvc.perform(get("/api/v1/videos/{id}/vocabularies", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].phonetic").value("/ˈɡɑːr.dən/"))
                .andExpect(jsonPath("$[0].exampleSentence").value("The garden is green."))
                .andExpect(jsonPath("$[0].exampleSentenceTranslate").value("Khu vườn xanh."))
                .andExpect(jsonPath("$[1].phonetic").hasJsonPath())
                .andExpect(jsonPath("$[1].exampleSentence").hasJsonPath())
                .andExpect(jsonPath("$[1].exampleSentenceTranslate").hasJsonPath());
        mvc.perform(get("/api/v1/videos/{id}/learning-content", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vocabularies[0].exampleSentenceTranslate").value("Khu vườn xanh."));
    }

    @Test
    void guestVideoEndpointsEnforceAccess() throws Exception {
        UUID locked = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Video này dành cho học viên ICE English."))
                .when(videos).requireGuestAccess(locked);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy video."))
                .when(videos).requireGuestAccess(unknown);

        mvc.perform(get("/api/v1/guest/videos/{id}/vocabularies", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].exampleSentence").value("The garden is green."));
        mvc.perform(get("/api/v1/guest/videos/{id}/learning-content", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vocabularies[0].phonetic").value("/ˈɡɑːr.dən/"));
        mvc.perform(get("/api/v1/guest/videos/{id}/vocabularies", locked)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/guest/videos/{id}/learning-content", locked)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/guest/videos/{id}/vocabularies", unknown)).andExpect(status().isNotFound());
        verify(videos, never()).getVocabularies(locked);
        verify(videos, never()).getLearningContent(locked);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminCanToggleGuestAccessForVideo() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(put("/api/v1/admin/videos/{id}/guest-access", id)
                        .contentType("application/json")
                        .content("{\"guestAccessible\":true}"))
                .andExpect(status().isNoContent());

        verify(videos).guestAccess(id, true);
    }

    @Test
    @WithMockUser(authorities = "TEACHER")
    void teacherCannotToggleGuestVideoAccess() throws Exception {
        mvc.perform(put("/api/v1/admin/videos/{id}/guest-access", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"guestAccessible\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TEACHER")
    void teacherCanUpdateVideoPosition() throws Exception {
        mvc.perform(put("/api/v1/admin/videos/{videoId}/position", videoId)
                        .contentType("application/json")
                        .content("{\"position\":\"2.3\"}"))
                .andExpect(status().isNoContent());

        verify(videos).updatePosition(videoId, "2.3");
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void studentCannotUpdateVideoPosition() throws Exception {
        mvc.perform(put("/api/v1/admin/videos/{videoId}/position", videoId)
                        .contentType("application/json")
                        .content("{\"position\":\"2.3\"}"))
                .andExpect(status().isForbidden());

        verify(videos, never()).updatePosition(any(), any());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminCanDeleteVideo() throws Exception {
        mvc.perform(delete("/api/v1/admin/videos/{videoId}", videoId))
                .andExpect(status().isNoContent());

        verify(videos).delete(videoId);
    }

    @Test
    @WithMockUser(authorities = "TEACHER")
    void teacherCannotDeleteVideo() throws Exception {
        mvc.perform(delete("/api/v1/admin/videos/{videoId}", videoId))
                .andExpect(status().isForbidden());

        verify(videos, never()).delete(any());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void positionValidationErrorsExposeMessage() throws Exception {
        doThrow(new VideoPositionException(HttpStatus.BAD_REQUEST,
                "Vị trí không hợp lệ. Ví dụ: 1, 1.1, 2.10."))
                .when(videos).updatePosition(videoId, "bad");

        mvc.perform(put("/api/v1/admin/videos/{videoId}/position", videoId)
                        .contentType("application/json")
                        .content("{\"position\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vị trí không hợp lệ. Ví dụ: 1, 1.1, 2.10."));
    }
}
