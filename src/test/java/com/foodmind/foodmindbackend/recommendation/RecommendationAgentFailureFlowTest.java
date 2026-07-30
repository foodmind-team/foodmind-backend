package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationAgentPort;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentCandidateResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
 * @date: 30/07/2026 10:14 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationAgentFailureFlowTest extends PostgreSqlContainerSupport {

    private static final AtomicReference<Function<RecommendationAgentCommand, AgentGenerationResult>> AGENT_RESPONSE =
            new AtomicReference<>();

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
    void invalidAgentOutputFallsBackAndPersistsSafeFailureCode() throws Exception {
        AGENT_RESPONSE.set(command -> AgentGenerationResult.success(
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.traceId(),
                "agent-trace-invalid-output",
                "SUCCEEDED",
                "recommendation-agent-demo-2026-07-30",
                "recommendation-features-v1",
                List.of(new AgentCandidateResult(
                        UUID.fromString("39999999-0000-4000-8000-000000000999"),
                        1,
                        RecommendationType.PERSONAL,
                        new BigDecimal("0.8700000"),
                        List.of(ReasonCode.WITHIN_BUDGET),
                        "Raw upstream invalid output should not leak.",
                        Map.of()))));
        String accessToken = read(register("agent-invalid@example.test", "Agent Invalid"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "agent-invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FALLBACK_SUCCEEDED"))
                .andExpect(jsonPath("$.modelStatus").value("INVALID_RESPONSE"))
                .andExpect(jsonPath("$.fallbackStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].explanation", not(containsString("Raw upstream"))))
                .andReturn();

        String sessionId = read(result, "$.sessionId");
        String failureCode = jdbcTemplate.queryForObject("""
                SELECT failure_code
                FROM recommendation_session
                WHERE id = ?::uuid
                """, String.class, sessionId);
        assertThat(failureCode).isEqualTo("UNKNOWN_ID");
    }

    @Test
    void unavailableAgentFallsBackWithoutRawUpstreamPayload() throws Exception {
        AGENT_RESPONSE.set(command -> AgentGenerationResult.failure(
                AgentFailureCode.INFERENCE_UNAVAILABLE,
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.traceId(),
                "agent-trace-unavailable"));
        String accessToken = read(register("agent-unavailable@example.test", "Agent Unavailable"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "agent-unavailable-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FALLBACK_SUCCEEDED"))
                .andExpect(jsonPath("$.modelStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.fallbackStatus").value("SUCCEEDED"))
                .andReturn();

        String sessionId = read(result, "$.sessionId");
        String failureCode = jdbcTemplate.queryForObject("""
                SELECT failure_code
                FROM recommendation_session
                WHERE id = ?::uuid
                """, String.class, sessionId);
        assertThat(failureCode).isEqualTo("INFERENCE_UNAVAILABLE");
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

    private String standardDinnerRequest() {
        return """
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
                """;
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @TestConfiguration
    static class StubAgentConfiguration {

        @Bean
        @Primary
        RecommendationAgentPort recommendationAgentPort() {
            return command -> AGENT_RESPONSE.get().apply(command);
        }
    }
}
