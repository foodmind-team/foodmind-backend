package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
class RecommendationRerecommendationFlowTest extends PostgreSqlContainerSupport {

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
    void laterSignalsValidateRatingRecordContextAndRerecommendCreatesLinkedSession() throws Exception {
        String accessToken = read(register("feedback-later@example.test", "Feedback Later"), "$.accessToken");
        MvcResult generated = generate(accessToken, "feedback-parent-generate-key");
        String parentSessionId = read(generated, "$.sessionId");
        String candidateId = read(generated, "$.items[0].candidateId");
        String mealId = read(generated, "$.items[0].mealId");
        String mealName = read(generated, "$.items[0].mealName");
        String placeId = read(generated, "$.items[0].placeId");
        String placeName = read(generated, "$.items[0].placeName");
        List<String> oldOrder = JsonPath.read(generated.getResponse().getContentAsString(), "$.items[*].candidateId");
        String foodRecordId = createFoodRecord(accessToken, mealId, mealName, placeId, placeName);

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", parentSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "feedback-invalid-rating-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "LATER_RATED",
                                  "rating": 6
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", parentSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "feedback-later-rating-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "%s",
                                  "eventType": "LATER_RATED",
                                  "rating": 4.5,
                                  "resultingFoodRecordId": "%s"
                                }
                                """.formatted(candidateId, foodRecordId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supervisedLabel").doesNotExist());

        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", parentSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "feedback-rerecommend-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "RERECOMMEND_REQUESTED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").doesNotExist());

        MvcResult child = mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "feedback-child-generate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentSessionId": "%s",
                                  "mealType": "DINNER",
                                  "maxBudget": 25,
                                  "currency": "SGD",
                                  "requestedFor": "2030-07-30T12:00:00Z"
                                }
                                """.formatted(parentSessionId)))
                .andExpect(status().isCreated())
                .andReturn();
        String childSessionId = read(child, "$.sessionId");
        String linkedParent = jdbcTemplate.queryForObject(
                "SELECT parent_session_id::text FROM recommendation_session WHERE id = ?::uuid",
                String.class,
                childSessionId);
        assertThat(linkedParent).isEqualTo(parentSessionId);

        MvcResult oldSession = mockMvc.perform(get("/api/v1/recommendations/{sessionId}", parentSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> rereadOrder = JsonPath.read(oldSession.getResponse().getContentAsString(), "$.items[*].candidateId");
        assertThat(rereadOrder).isEqualTo(oldOrder);
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

    private String createFoodRecord(
            String accessToken,
            String mealId,
            String mealName,
            String placeId,
            String placeName) throws Exception {
        return read(mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealId": "%s",
                                  "mealNameSnapshot": "%s",
                                  "placeId": "%s",
                                  "placeNameSnapshot": "%s",
                                  "occurredAt": "2026-07-29T04:15:00Z",
                                  "rating": 4.5,
                                  "visibility": "PRIVATE"
                                }
                                """.formatted(mealId, mealName, placeId, placeName)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
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
