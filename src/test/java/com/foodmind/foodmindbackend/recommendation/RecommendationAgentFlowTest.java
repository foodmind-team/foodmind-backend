package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationAgentPort;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentCandidateResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationAgentFlowTest extends PostgreSqlContainerSupport {

    private static final AtomicReference<Function<RecommendationAgentCommand, AgentGenerationResult>> AGENT_RESPONSE =
            new AtomicReference<>(RecommendationAgentFlowTest::successfulAgentResult);
    private static final AtomicBoolean REMOTE_CALL_OBSERVED_TRANSACTION = new AtomicBoolean(true);

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
        AGENT_RESPONSE.set(RecommendationAgentFlowTest::successfulAgentResult);
        REMOTE_CALL_OBSERVED_TRANSACTION.set(true);
    }

    @Test
    void generatePersistsValidatedAgentResultOutsideDatabaseTransaction() throws Exception {
        String accessToken = read(register("agent-success@example.test", "Agent Success"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "agent-success-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardDinnerRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.modelStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.modelVersion").value("recommendation-agent-demo-2026-07-30"))
                .andExpect(jsonPath("$.fallbackStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.fallbackVersion").doesNotExist())
                .andExpect(jsonPath("$.items[0].recommendationType").value("PERSONAL"))
                .andExpect(jsonPath("$.items[0].modelScore").value(0.87))
                .andExpect(jsonPath("$.items[0].reasonCodes[0]").value("WITHIN_BUDGET"))
                .andReturn();

        String sessionId = read(result, "$.sessionId");
        String modelScore = jdbcTemplate.queryForObject("""
                SELECT model_score::text
                FROM recommendation_candidate
                WHERE session_id = ?::uuid
                  AND eligibility_status = 'RETURNED'
                """, String.class, sessionId);
        assertThat(modelScore).isEqualTo("0.8700000");
        assertThat(REMOTE_CALL_OBSERVED_TRANSACTION).isFalse();
    }

    private static AgentGenerationResult successfulAgentResult(RecommendationAgentCommand command) {
        return AgentGenerationResult.success(
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.traceId(),
                "agent-trace-success",
                "SUCCEEDED",
                "recommendation-agent-demo-2026-07-30",
                "recommendation-features-v1",
                List.of(new AgentCandidateResult(
                        command.candidates().get(0).candidateId(),
                        1,
                        RecommendationType.PERSONAL,
                        new BigDecimal("0.8700000"),
                        List.of(ReasonCode.WITHIN_BUDGET),
                        "Selected by the private Agent from bounded candidate features.",
                        Map.of())));
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
            return command -> {
                REMOTE_CALL_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                return AGENT_RESPONSE.get().apply(command);
            };
        }
    }
}
