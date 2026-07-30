package com.foodmind.foodmindbackend.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentGenerationResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentIngredientResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentStepResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentWarningResult;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
 * @date: 30/07/2026 12:10 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CookingPlanFlowTest extends PostgreSqlContainerSupport {

    private static final AtomicReference<Function<CookingAgentCommand, CookingAgentGenerationResult>> AGENT_RESPONSE =
            new AtomicReference<>(CookingPlanFlowTest::successfulAgentResult);
    private static final AtomicBoolean REMOTE_CALL_OBSERVED_TRANSACTION = new AtomicBoolean(true);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE idempotency_record, cooking_plan, auth_session, app_user CASCADE
                """);
        AGENT_RESPONSE.set(CookingPlanFlowTest::successfulAgentResult);
        REMOTE_CALL_OBSERVED_TRANSACTION.set(true);
    }

    @Test
    void generatePersistsValidatedPlanOutsideDatabaseTransactionAndReplaysIdempotently() throws Exception {
        String accessToken = read(register("cooking-success@example.test", "Cooking Success"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-success-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.sourceRecipeId").value("30000000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.fallbackStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.inputs[0].ingredientName").value("Firm tofu"))
                .andExpect(jsonPath("$.ingredients[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.steps[0].stepNo").value(1))
                .andReturn();

        String planId = read(result, "$.planId");
        assertThat(REMOTE_CALL_OBSERVED_TRANSACTION).isFalse();

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-success-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId));

        Long planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM cooking_plan", Long.class);
        assertThat(planCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.steps[1].instruction").value("Brown the tofu, then add broccoli and ginger."));

        mockMvc.perform(get("/api/v1/cooking-plans/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].planId").value(planId))
                .andExpect(jsonPath("$.items[0].stepCount").value(2));
    }

    @Test
    void ownerIsolationReturnsNotFound() throws Exception {
        String ownerToken = read(register("cooking-owner@example.test", "Cooking Owner"), "$.accessToken");
        String otherToken = read(register("cooking-other@example.test", "Cooking Other"), "$.accessToken");
        String planId = read(mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header("Idempotency-Key", "owner-plan-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andReturn(), "$.planId");

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void noControlledRecipeMatchPersistsSafeTerminalState() throws Exception {
        String accessToken = read(register("cooking-nomatch@example.test", "Cooking Nomatch"), "$.accessToken");

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-nomatch-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noMatchRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NO_VALID_RECIPE"))
                .andExpect(jsonPath("$.fallbackStatus").value("NO_VALID_RECIPE"))
                .andExpect(jsonPath("$.failureCode").value("NO_RECIPE_MATCH"))
                .andExpect(jsonPath("$.steps").isArray())
                .andExpect(jsonPath("$.steps.length()").value(0));
    }

    @Test
    void invalidAgentRecipeIsRejectedWithoutRawText() throws Exception {
        AGENT_RESPONSE.set(command -> CookingAgentGenerationResult.success(
                command.contractVersion(),
                command.requestId(),
                command.planId(),
                command.traceId(),
                "agent-trace-invalid-recipe",
                UUID.fromString("39999999-0000-4000-8000-000000000999"),
                2,
                35,
                new BigDecimal("9.50"),
                "SGD",
                List.of(new CookingAgentIngredientResult(1, "Raw upstream invalid ingredient", null, null, "AVAILABLE")),
                List.of(new CookingAgentStepResult(1, "Raw upstream invalid step.")),
                List.of()));
        String accessToken = read(register("cooking-invalid@example.test", "Cooking Invalid"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("UNKNOWN_RECIPE"))
                .andExpect(jsonPath("$.ingredients.length()").value(0))
                .andExpect(jsonPath("$.steps.length()").value(0))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Raw upstream");
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadConflicts() throws Exception {
        String accessToken = read(register("cooking-conflict@example.test", "Cooking Conflict"), "$.accessToken");

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chickpeaRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private static CookingAgentGenerationResult successfulAgentResult(CookingAgentCommand command) {
        UUID recipeId = command.candidates().get(0).recipeId();
        return CookingAgentGenerationResult.success(
                command.contractVersion(),
                command.requestId(),
                command.planId(),
                command.traceId(),
                "agent-trace-cooking-success",
                recipeId,
                2,
                35,
                new BigDecimal("9.50"),
                "SGD",
                List.of(
                        new CookingAgentIngredientResult(1, "Firm tofu", new BigDecimal("300"), "g", "AVAILABLE"),
                        new CookingAgentIngredientResult(2, "Broccoli", new BigDecimal("180"), "g", "TO_BUY")),
                List.of(
                        new CookingAgentStepResult(1, "Cook the jasmine rice according to its package directions."),
                        new CookingAgentStepResult(2, "Brown the tofu, then add broccoli and ginger.")),
                List.of(new CookingAgentWarningResult(1, "BUDGET_ESTIMATE_ONLY", "Costs are an estimate, not a live price.")));
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

    private String tofuRequest() {
        return """
                {
                  "ingredients": [
                    { "ingredientName": "Firm tofu", "quantity": 300, "unit": "g", "source": "MANUAL" },
                    { "ingredientName": "Fresh ginger", "quantity": 15, "unit": "g", "source": "MANUAL" }
                  ],
                  "servings": 2,
                  "maxMinutes": 60,
                  "maxBudget": 20,
                  "currency": "SGD",
                  "requiredDietaryTagCodes": ["VEGAN"],
                  "avoidAllergenCodes": []
                }
                """;
    }

    private String chickpeaRequest() {
        return """
                {
                  "ingredients": [
                    { "ingredientName": "Chickpeas", "quantity": 480, "unit": "g", "source": "MANUAL" }
                  ],
                  "servings": 4,
                  "maxMinutes": 60,
                  "maxBudget": 20,
                  "currency": "SGD",
                  "requiredDietaryTagCodes": ["VEGAN"],
                  "avoidAllergenCodes": []
                }
                """;
    }

    private String noMatchRequest() {
        return """
                {
                  "ingredients": [
                    { "ingredientName": "Dragonfruit", "quantity": 1, "unit": "item", "source": "MANUAL" }
                  ],
                  "servings": 2,
                  "maxMinutes": 20,
                  "maxBudget": 5,
                  "currency": "SGD",
                  "requiredDietaryTagCodes": ["VEGAN"],
                  "avoidAllergenCodes": []
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
        CookingAgentPort cookingAgentPort() {
            return command -> {
                REMOTE_CALL_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                return AGENT_RESPONSE.get().apply(command);
            };
        }
    }
}
