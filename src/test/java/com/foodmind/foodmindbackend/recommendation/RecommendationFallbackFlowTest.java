package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationAgentPort;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
 * @date: 30/07/2026 06:54 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationFallbackFlowTest extends PostgreSqlContainerSupport {

    private static final String DINNER_MEAL_ID = "20000000-0000-4000-8000-000000000003";
    private static final String DINNER_PLACE_ID = "21000000-0000-4000-8000-000000000003";
    private static final String DINNER_CUISINE_ID = "10000000-0000-4000-8000-000000000004";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE idempotency_record, want_to_try, group_recommendation_share, recommendation_candidate,
                    recommendation_session, food_record, group_invitation, group_membership, trusted_group,
                    auth_session, app_user CASCADE
                """);
    }

    @Test
    void generatePersistsStableIdempotentFallbackAndOwnerScopedHistory() throws Exception {
        String primaryToken = read(register("recommendation-primary@example.test", "Recommendation Primary"), "$.accessToken");
        String secondaryToken = read(register("recommendation-secondary@example.test", "Recommendation Secondary"), "$.accessToken");
        replacePreferences(primaryToken);

        MvcResult generated = mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "recommendation-flow-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest("25")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FALLBACK_SUCCEEDED"))
                .andExpect(jsonPath("$.fallbackStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.fallbackVersion").value("fallback-v1"))
                .andExpect(jsonPath("$.items.length()", lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].reasonCodes").isArray())
                .andReturn();

        String sessionId = read(generated, "$.sessionId");
        String firstCandidateId = read(generated, "$.items[0].candidateId");

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "recommendation-flow-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest("25")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header("Idempotency-Key", "recommendation-flow-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest("24")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/v1/recommendations/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].candidateId").value(firstCandidateId));
        mockMvc.perform(get("/api/v1/recommendations/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/recommendations/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sessionId").value(sessionId));
        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/try-another", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void budgetIsASoftPreferenceAndStillReturnsFallbackCandidates() throws Exception {
        String accessToken = read(register("recommendation-empty@example.test", "Recommendation Empty"), "$.accessToken");

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "recommendation-empty-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest("0.01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FALLBACK_SUCCEEDED"))
                .andExpect(jsonPath("$.fallbackStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items.length()", lessThanOrEqualTo(3)));
    }

    @Test
    void distanceWithoutCoordinatesIsRejectedBeforePersistence() throws Exception {
        String accessToken = read(register("recommendation-distance@example.test", "Recommendation Distance"), "$.accessToken");

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "recommendation-distance-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "maxDistanceKm": 900,
                                  "requestedFor": "2030-07-30T12:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendation_session", Integer.class)).isZero();
    }

    @Test
    void authorisedActiveGroupEvidenceCanDriveGroupInspiredCandidate() throws Exception {
        String ownerToken = read(register("recommendation-owner@example.test", "Recommendation Owner"), "$.accessToken");
        String memberToken = read(register("recommendation-member@example.test", "Recommendation Member"), "$.accessToken");
        String groupId = createGroup(ownerToken, "Recommendation Group");
        joinGroup(ownerToken, memberToken, groupId);
        createGroupFoodRecord(memberToken, groupId);

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header("Idempotency-Key", "recommendation-group-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": "%s",
                                  "mealType": "DINNER",
                                  "maxBudget": 25,
                                  "currency": "SGD",
                                  "latitude": 1.3496,
                                  "longitude": 103.8737,
                                  "maxDistanceKm": 20,
                                  "requestedFor": "2030-07-30T12:00:00Z"
                                }
                                """.formatted(groupId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[*].recommendationType", hasItem("GROUP_INSPIRED")))
                .andExpect(jsonPath("$.items[*].reasonCodes[*]", hasItem("TRUSTED_GROUP_RATING")));

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .header("Idempotency-Key", "recommendation-unauthorised-group-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": "00000000-0000-4000-8000-000000000000",
                                  "mealType": "DINNER",
                                  "maxBudget": 25,
                                  "currency": "SGD"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void temporaryAllergenConstraintExcludesMatchingMeals() throws Exception {
        String accessToken = read(register("recommendation-allergen@example.test", "Recommendation Allergen"), "$.accessToken");

        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "recommendation-allergen-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "maxBudget": 25,
                                  "currency": "SGD",
                                  "requestedFor": "2030-07-30T12:00:00Z",
                                  "constraints": {
                                    "avoidAllergenCodes": ["FISH", "SESAME"]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[*].mealName", not(hasItem("Grilled Salmon Donburi"))));
    }

    private void replacePreferences(String accessToken) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budgetMax": 25,
                                  "currency": "SGD",
                                  "spiceTolerance": 4,
                                  "likedCuisineCodes": ["INDIAN"],
                                  "preferredMealTypes": ["DINNER"]
                                }
                                """))
                .andExpect(status().isOk());
    }

    private String standardDinnerRequest(String maxBudget) {
        return """
                {
                  "mealType": "DINNER",
                  "maxBudget": %s,
                  "currency": "SGD",
                  "area": "Tiong Bahru",
                  "latitude": 1.284,
                  "longitude": 103.832,
                  "maxDistanceKm": 12,
                  "mood": "COMFORT",
                  "requestedFor": "2030-07-30T12:00:00Z"
                }
                """.formatted(maxBudget);
    }

    private String createGroup(String accessToken, String name) throws Exception {
        return read(mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void joinGroup(String ownerToken, String memberToken, String groupId) throws Exception {
        String invitationToken = read(mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":5}"))
                .andExpect(status().isCreated())
                .andReturn(), "$.token");
        mockMvc.perform(post("/api/v1/group-invitations/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(invitationToken)))
                .andExpect(status().isOk());
    }

    private void createGroupFoodRecord(String accessToken, String groupId) throws Exception {
        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealId": "%s",
                                  "mealNameSnapshot": "Chana Masala with Rice",
                                  "placeId": "%s",
                                  "placeNameSnapshot": "Serangoon Vegetarian Table",
                                  "cuisineId": "%s",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "price": 9.50,
                                  "currency": "SGD",
                                  "rating": 5,
                                  "comment": "Group recommendation fixture",
                                  "visibility": "GROUP",
                                  "groupId": "%s"
                                }
                                """.formatted(DINNER_MEAL_ID, DINNER_PLACE_ID, DINNER_CUISINE_ID, groupId)))
                .andExpect(status().isCreated());
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

    @TestConfiguration
    static class UnavailableAgentConfiguration {

        @Bean
        @Primary
        RecommendationAgentPort recommendationAgentPort() {
            return command -> AgentGenerationResult.failure(
                    AgentFailureCode.CONNECTION_ERROR,
                    command.contractVersion(),
                    command.requestId(),
                    command.sessionId(),
                    command.traceId(),
                    "offline-fallback-test-agent-trace");
        }
    }
}
