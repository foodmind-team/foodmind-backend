package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationFeedbackControllerTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE idempotency_record, recommendation_feedback, want_to_try, group_recommendation_share,
                    recommendation_candidate, recommendation_session, food_record, group_invitation, group_membership,
                    trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void submitFeedbackIsAppendOnlyIdempotentAndOwnerScoped() throws Exception {
        String primaryToken = read(register("feedback-primary@example.test", "Feedback Primary"), "$.accessToken");
        String secondaryToken = read(register("feedback-secondary@example.test", "Feedback Secondary"), "$.accessToken");
        MvcResult generated = generate(primaryToken, "feedback-generate-key");
        String sessionId = read(generated, "$.sessionId");
        String firstCandidateId = read(generated, "$.items[0].candidateId");
        String secondCandidateId = read(generated, "$.items[1].candidateId");

        MvcResult accepted = mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "feedback-accept-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "ACCEPTED"
                                }
                                """.formatted(firstCandidateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("ACCEPTED"))
                .andExpect(jsonPath("$.supervisedLabel").value(1))
                .andReturn();
        String feedbackId = read(accepted, "$.feedbackId");

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "feedback-accept-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "ACCEPTED"
                                }
                                """.formatted(firstCandidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(feedbackId));

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "feedback-accept-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "REJECTED",
                                  "reasonCode": "OTHER"
                                }
                                """.formatted(firstCandidateId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "feedback-contradict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "REJECTED",
                                  "reasonCode": "OTHER"
                                }
                                """.formatted(firstCandidateId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "feedback-reject-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "REJECTED",
                                  "reasonCode": "TOO_EXPENSIVE"
                                }
                                """.formatted(secondCandidateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supervisedLabel").value(0))
                .andExpect(jsonPath("$.effectiveUntil").exists());

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken))
                        .header("Idempotency-Key", "feedback-secondary-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "ACCEPTED"
                                }
                                """.formatted(firstCandidateId)))
                .andExpect(status().isNotFound());

        Integer feedbackRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recommendation_feedback",
                Integer.class);
        assertThat(feedbackRows).isEqualTo(2);
    }

    @Test
    void doNotRecommendExcludesTheOfferingFromFutureSessionsOnlyForTheOwner() throws Exception {
        String primaryToken = read(register("feedback-never-primary@example.test", "Never Primary"), "$.accessToken");
        String secondaryToken = read(register("feedback-never-secondary@example.test", "Never Secondary"), "$.accessToken");
        MvcResult parent = generate(primaryToken, "feedback-never-parent-key");
        String parentSessionId = read(parent, "$.sessionId");
        String candidateId = read(parent, "$.items[0].candidateId");
        Map<String, Object> target = jdbcTemplate.queryForMap("""
                SELECT offering.meal_id::text AS meal_id, offering.place_id::text AS place_id
                FROM recommendation_candidate candidate
                JOIN place_meal offering ON offering.id = candidate.place_meal_id
                WHERE candidate.id = ?::uuid
                """, candidateId);

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", parentSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "feedback-never-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "REJECTED",
                                  "reasonCode": "DO_NOT_RECOMMEND"
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supervisedLabel").value(0))
                .andExpect(jsonPath("$.effectiveUntil").doesNotExist());

        String nextSessionId = read(generate(primaryToken, "feedback-never-next-key"), "$.sessionId");
        Integer ownerCandidates = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM recommendation_candidate candidate
                JOIN place_meal offering ON offering.id = candidate.place_meal_id
                WHERE candidate.session_id = ?::uuid
                  AND offering.meal_id = ?::uuid
                  AND offering.place_id = ?::uuid
                """, Integer.class, nextSessionId, target.get("meal_id"), target.get("place_id"));
        assertThat(ownerCandidates).isZero();

        String secondarySessionId = read(generate(secondaryToken, "feedback-never-secondary-generate-key"), "$.sessionId");
        Integer otherUserCandidates = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM recommendation_candidate candidate
                JOIN place_meal offering ON offering.id = candidate.place_meal_id
                WHERE candidate.session_id = ?::uuid
                  AND offering.meal_id = ?::uuid
                  AND offering.place_id = ?::uuid
                """, Integer.class, secondarySessionId, target.get("meal_id"), target.get("place_id"));
        assertThat(otherUserCandidates).isOne();

        mockMvc.perform(get("/api/v1/recommendations/{sessionId}", parentSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].candidateId").value(candidateId));
    }

    private MvcResult generate(String accessToken, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "maxBudget": 25,
                                  "currency": "SGD",
                                  "area": "Tiong Bahru",
                                  "latitude": 1.284,
                                  "longitude": 103.832,
                                  "maxDistanceKm": 12,
                                  "mood": "COMFORT",
                                  "requestedFor": "2030-07-30T12:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult register(String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "correct horse battery",
                                  "clientType": "WEB",
                                  "deviceLabel": "JUnit"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
