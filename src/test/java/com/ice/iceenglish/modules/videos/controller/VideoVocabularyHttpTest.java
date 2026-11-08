package com.ice.iceenglish.modules.videos.controller;

import com.ice.iceenglish.config.SecurityConfig;
import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.exams.service.ExamSnapshotService;
import com.ice.iceenglish.modules.guest.GuestController;
import com.ice.iceenglish.modules.guest.GuestService;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.service.VideoService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({VideoController.class, GuestController.class})
@Import({SecurityConfig.class, GuestService.class, VideoVocabularyHttpTest.TestClock.class})
@TestPropertySource(properties = "app.guest.video-id=11111111-1111-1111-1111-111111111111")
class VideoVocabularyHttpTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private VideoService videos;
    @MockitoBean private ExamService exams;
    @MockitoBean private ExamSnapshotService snapshots;
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
    void guestEndpointsReturnSampleOnly() throws Exception {
        mvc.perform(get("/api/v1/guest/videos/{id}/vocabularies", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].exampleSentence").value("The garden is green."));
        mvc.perform(get("/api/v1/guest/videos/{id}/learning-content", videoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vocabularies[0].phonetic").value("/ˈɡɑːr.dən/"));
        UUID locked = UUID.randomUUID();
        mvc.perform(get("/api/v1/guest/videos/{id}/vocabularies", locked)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/guest/videos/{id}/learning-content", locked)).andExpect(status().isNotFound());
        verify(videos, never()).getVocabularies(locked);
        verify(videos, never()).getLearningContent(locked);
    }
}
