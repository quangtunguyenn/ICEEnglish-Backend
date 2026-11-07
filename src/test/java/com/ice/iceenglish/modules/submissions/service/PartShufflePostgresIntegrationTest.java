package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shuffle.integration", matches = "true")
class PartShufflePostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    private final JsonMapper json = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL_EXAM", "MOCK"})
    @Transactional
    void importStartReloadSubmitReviewAndAuthorizationUsePersistedOrder(String mode) throws Exception {
        String student = "shuffle-test-" + UUID.randomUUID();
        String other = "shuffle-test-" + UUID.randomUUID();
        users.save(User.builder().username(student).passwordHash("unused").role("STUDENT").build());
        users.save(User.builder().username(other).passwordHash("unused").role("STUDENT").build());

        String document = """
                {"exam":{"title":"Shuffle integration test","shuffle_parts":true%s},"parts":[
                  {"part_type":"GRAMMAR","part_name":"A1","order_index":1,"shuffle_group":"A","questions":[
                    {"order_index":1,"content":"One","options":{"A":"a","B":"b","C":"c","D":"d"},"correct_option":"A"}]},
                  {"part_type":"GRAMMAR","part_name":"Fixed","order_index":2,"fixed_position":true,"questions":[
                    {"order_index":2,"content":"Two","options":{"A":"a","B":"b","C":"c","D":"d"},"correct_option":"A"}]},
                  {"part_type":"GRAMMAR","part_name":"A2","order_index":3,"shuffle_group":"A","questions":[
                    {"order_index":3,"content":"Three","options":{"A":"a","B":"b","C":"c","D":"d"},"correct_option":"A"}]}
                ]}
                """.formatted(mode.equals("MOCK") ? ",\"duration_minutes\":50" : "");
        MockMultipartFile file = new MockMultipartFile("file", "exam.json", "application/json",
                document.getBytes(StandardCharsets.UTF_8));
        JsonNode imported = response(mvc.perform(multipart("/api/v1/exams/import")
                        .file(file).param("examType", mode)
                        .with(user("teacher").authorities(new SimpleGrantedAuthority("TEACHER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String examId = imported.get("id").asString();
        JsonNode preview = response(mvc.perform(get("/api/v1/exams/{id}/preview", examId)
                        .with(user("teacher").authorities(new SimpleGrantedAuthority("TEACHER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(preview.get("shuffleParts").asBoolean());
        String fixedId = preview.get("parts").get(1).get("id").asString();
        assertTrue(preview.get("parts").get(1).get("fixedPosition").asBoolean());
        mvc.perform(patch("/api/v1/exams/{id}/publish", examId)
                        .with(user("teacher").authorities(new SimpleGrantedAuthority("TEACHER"))))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        JsonNode started = response(mvc.perform(post("/api/v1/submissions/start/{id}", examId)
                        .with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String submissionId = started.get("submissionId").asString();
        List<String> order = strings(started.get("partOrder"));
        assertEquals(3, order.size());
        assertEquals(fixedId, order.get(1));
        entityManager.flush();
        entityManager.clear();
        String stored = (String) entityManager.createNativeQuery(
                        "select part_order::text from submissions where id = cast(:id as uuid)")
                .setParameter("id", submissionId).getSingleResult();
        assertEquals(order, strings(json.readTree(stored)));

        JsonNode resumed = response(mvc.perform(post("/api/v1/submissions/start/{id}", examId)
                        .with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(submissionId, resumed.get("submissionId").asString());
        assertEquals(order, strings(resumed.get("partOrder")));
        JsonNode exam = response(mvc.perform(get("/api/v1/exams/{id}", examId)
                        .param("submissionId", submissionId).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> questionIds = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            JsonNode part = exam.get("parts").get(i);
            assertEquals(order.get(i), part.get("id").asString());
            assertEquals(i + 1, part.get("displayIndex").asInt());
            assertEquals(i + 1, part.get("questions").get(0).get("displayNumber").asInt());
            questionIds.add(part.get("questions").get(0).get("id").asString());
        }
        mvc.perform(get("/api/v1/exams/{id}", examId).param("submissionId", submissionId)
                        .with(user(other).roles("STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exams/{id}", UUID.randomUUID()).param("submissionId", submissionId)
                        .with(user(student).roles("STUDENT")))
                .andExpect(status().isNotFound());

        if (mode.equals("MOCK")) {
            for (int i = order.size() - 1; i >= 0; i--) {
                mvc.perform(put("/api/v1/submissions/{submissionId}/parts/{partId}/time",
                                submissionId, order.get(i)).contentType("application/json")
                                .content("{\"timeSpentSeconds\":30}").with(user(student).roles("STUDENT")))
                        .andExpect(status().isNoContent());
            }
            JsonNode logs = response(mvc.perform(get("/api/v1/submissions/{id}/part-logs", submissionId)
                            .with(user(student).roles("STUDENT")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for (int i = 0; i < order.size(); i++) {
                assertEquals(order.get(i), logs.get(i).get("partId").asString());
                assertEquals(i + 1, logs.get(i).get("displayIndex").asInt());
            }
            mvc.perform(get("/api/v1/submissions/{id}/part-logs", submissionId)
                            .with(user(other).roles("STUDENT")))
                    .andExpect(status().isBadRequest());
        }

        String body = """
                {"submissionId":"%s","exam_id":"%s","answers":[
                  {"questionId":"%s","selectedOpt":"A"},
                  {"questionId":"%s","selectedOpt":"A"},
                  {"questionId":"%s","selectedOpt":"A"}]}
                """.formatted(submissionId, examId, questionIds.get(0), questionIds.get(1), questionIds.get(2));
        JsonNode result = response(mvc.perform(post("/api/v1/submissions/submit")
                        .contentType("application/json").content(body).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(3, result.get("totalCorrect").asInt());
        entityManager.flush();
        entityManager.clear();
        String reviewUrl = mode.equals("MOCK")
                ? "/api/v1/submissions/mock/" + examId + "/review"
                : "/api/v1/submissions/practice/" + submissionId + "/review";
        JsonNode review = response(mvc.perform(get(reviewUrl)
                        .with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(order.get(i), result.get("parts").get(i).get("partId").asString());
            assertEquals(order.get(i), review.get("parts").get(i).get("partId").asString());
            assertEquals(questionIds.get(i), review.get("details").get(i).get("questionId").asString());
            assertEquals(i + 1, review.get("details").get(i).get("displayNumber").asInt());
        }
        if (mode.equals("GENERAL_EXAM")) {
            mvc.perform(get(reviewUrl).with(user(other).roles("STUDENT")))
                    .andExpect(status().isNotFound());
        }
    }

    private JsonNode response(String content) {
        return json.readTree(content);
    }

    private List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(node.asString()));
        return result;
    }
}
