package com.ice.iceenglish.modules.submissions.controller;

import com.ice.iceenglish.modules.submissions.exception.SubmissionExceptionHandler;
import com.ice.iceenglish.modules.submissions.exception.SubmissionStateException;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionConflictHttpTest {
    @Test
    void expectedAttemptConflictReturns409() throws Exception {
        SubmissionService service = mock(SubmissionService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SubmissionController(service))
                .setControllerAdvice(new SubmissionExceptionHandler())
                .build();
        UUID examId = UUID.randomUUID();
        when(service.startExam(examId, "student"))
                .thenThrow(new SubmissionStateException("Attempt already used"));

        mvc.perform(post("/api/v1/submissions/start/{examId}", examId)
                        .principal(new TestingAuthenticationToken("student", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Attempt already used"));
    }
}
