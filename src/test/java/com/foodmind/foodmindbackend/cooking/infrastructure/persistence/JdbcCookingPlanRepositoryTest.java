package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentAssumption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentCompletionItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDecision;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDishCompletion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentLotAllocation;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentMiseEnPlaceItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentQuestionOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRepairOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTimelineTask;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class JdbcCookingPlanRepositoryTest extends PostgreSqlContainerSupport {

    @Autowired
    private CookingPlanRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("TRUNCATE TABLE cooking_plan, inventory_lot, inventory_item, app_user CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void readyPlanRoundTripMaterialisesChildTables() {
        UUID userId = insertUser("ready@example.test");
        UUID itemId = insertItem("chicken breast");
        UUID lotId = insertLot(itemId, userId, "500", "0");

        UUID recipeId = UUID.randomUUID();
        String requestId = "req-ready-1";
        AgentGeneratePlanRequest request = request(userId, requestId, recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-1", json(request));

        CookingPlanResult processing = repository.findOwned(userId, planId).orElseThrow();
        assertThat(processing.status()).isEqualTo("PROCESSING");
        assertThat(processing.createdAt()).isNotNull();

        AgentReadyPlanResponse ready = readyResponse(requestId, lotId);
        repository.completeReady(userId, planId, ready, json(ready));

        CookingPlanResult result = repository.findOwned(userId, planId).orElseThrow();
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.solverStatus()).isEqualTo("OPTIMAL");
        assertThat(result.makespanMinutes()).isEqualTo(54);
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).sourceType()).isEqualTo("CATALOGUE");
        assertThat(result.timeline()).hasSize(1);
        assertThat(result.timeline().get(0).instruction()).isEqualTo("Pan-fry the tofu.");
        assertThat(result.miseEnPlace()).hasSize(1);
        assertThat(result.dishCompletions()).hasSize(1);
        assertThat(result.completionChecklist()).hasSize(1);
        assertThat(result.completionChecklist().get(0).allocations()).hasSize(1);
        assertThat(result.completionChecklist().get(0).allocations().get(0).inventoryLotId()).isEqualTo(lotId);
        assertThat(result.explanation()).isEqualTo("explanation");
        assertThat(result.explanationSource()).isEqualTo("deterministic");
    }

    @Test
    void confirmationRoundTripReadsBackQuestionsAndDecisions() {
        UUID userId = insertUser("confirm@example.test");
        UUID recipeId = UUID.randomUUID();
        String requestId = "req-confirm-1";
        AgentGeneratePlanRequest request = request(userId, requestId, recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-2", json(request));

        AgentConfirmationPlanResponse confirmation = new AgentConfirmationPlanResponse(
                requestId, "NEEDS_CONFIRMATION",
                List.of(new AgentAssumption("assuming 200 C", new BigDecimal("0.82"), List.of())),
                List.of(new AgentRepairOption("opt-1", "reduce_servings", "Reduce to 2 servings",
                        List.of("servings 4 -> 2"), List.of("feasible"), "validated")),
                List.of("Would you like to proceed?"),
                List.of(new AgentConfirmationQuestion("q-1", "recipe.r-1.assumptions", "Accept?",
                        "CHOICE", List.of(new AgentQuestionOption("accept", "Accept", true)), true, "200 C")),
                List.of(new AgentDecision("opt-1", "reduce_servings", Map.of("servings", 2), "req-confirm-1:v1")),
                "req-confirm-1:v1", null);
        repository.completeConfirmation(userId, planId, confirmation, json(confirmation));

        CookingPlanResult result = repository.findOwned(userId, planId).orElseThrow();
        assertThat(result.status()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(result.planRevision()).isEqualTo("req-confirm-1:v1");
        assertThat(result.assumptions()).hasSize(1);
        assertThat(result.repairOptions()).hasSize(1);
        assertThat(result.questions()).containsExactly("Would you like to proceed?");
        assertThat(result.confirmationQuestions()).hasSize(1);
        assertThat(result.confirmationQuestions().get(0).options().get(0).value()).isEqualTo("accept");
        assertThat(result.decisions()).hasSize(1);
        assertThat(result.decisions().get(0).payload()).containsEntry("servings", 2);
    }

    @Test
    void infeasibleRoundTripReadsBackReasons() {
        UUID userId = insertUser("infeasible@example.test");
        UUID recipeId = UUID.randomUUID();
        String requestId = "req-infeasible-1";
        AgentGeneratePlanRequest request = request(userId, requestId, recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-3", json(request));

        AgentInfeasiblePlanResponse infeasible = new AgentInfeasiblePlanResponse(
                requestId, "INFEASIBLE",
                List.of("Insufficient 'chilli': need 60 g, have 30 g"), List.of("Use dried chilli"));
        repository.completeInfeasible(userId, planId, infeasible, json(infeasible));

        CookingPlanResult result = repository.findOwned(userId, planId).orElseThrow();
        assertThat(result.status()).isEqualTo("INFEASIBLE");
        assertThat(result.reasons()).containsExactly("Insufficient 'chilli': need 60 g, have 30 g");
        assertThat(result.safeAlternatives()).containsExactly("Use dried chilli");
    }

    @Test
    void failedRoundTripReadsBackError() {
        UUID userId = insertUser("failed@example.test");
        UUID recipeId = UUID.randomUUID();
        String requestId = "req-failed-1";
        AgentGeneratePlanRequest request = request(userId, requestId, recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-4", json(request));

        AgentFailedPlanResponse failed =
                new AgentFailedPlanResponse("FAILED", "SCHEDULE_UNKNOWN", "c-1", "timeout");
        repository.completeFailed(userId, planId, CookingAgentFailureCode.SCHEDULE_UNKNOWN, failed, json(failed));

        CookingPlanResult result = repository.findOwned(userId, planId).orElseThrow();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("SCHEDULE_UNKNOWN");
        assertThat(result.errorMessage()).isEqualTo("timeout");
    }

    @Test
    void failedRoundTripDoesNotParseNonConformingRawAgentResponse() {
        UUID userId = insertUser("failed-invalid-raw@example.test");
        UUID recipeId = UUID.randomUUID();
        AgentGeneratePlanRequest request = request(userId, "req-failed-invalid-raw-1", recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-invalid-raw", json(request));

        repository.completeFailed(
                userId,
                planId,
                CookingAgentFailureCode.SCHEMA_MISMATCH,
                null,
                "{\"status\":\"NEEDS_CONFIRMATION\",\"unexpected\":true}");

        CookingPlanResult result = repository.findOwned(userId, planId).orElseThrow();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("SCHEMA_MISMATCH");
    }

    @Test
    void ownerIsolationReturnsEmpty() {
        UUID owner = insertUser("owner@example.test");
        UUID other = insertUser("other@example.test");
        UUID recipeId = UUID.randomUUID();
        AgentGeneratePlanRequest request = request(owner, "req-owner-1", recipeId);
        UUID planId = repository.createProcessing(owner, request, sources(recipeId), "trace-5", json(request));

        Optional<CookingPlanResult> result = repository.findOwned(other, planId);

        assertThat(result).isEmpty();
    }

    @Test
    void historyCountsSourcesAndTasks() {
        UUID userId = insertUser("history@example.test");
        UUID recipeId = UUID.randomUUID();
        String requestId = "req-history-1";
        AgentGeneratePlanRequest request = request(userId, requestId, recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-6", json(request));
        repository.completeReady(userId, planId, readyResponse(requestId, null), json(readyResponse(requestId, null)));

        assertThat(repository.countOwned(userId)).isEqualTo(1);
        assertThat(repository.findOwnedPage(userId, 0, 20))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.status()).isEqualTo("READY");
                    assertThat(summary.sourceCount()).isEqualTo(1);
                    assertThat(summary.taskCount()).isEqualTo(1);
                    assertThat(summary.makespanMinutes()).isEqualTo(54);
                });
    }

    @Test
    void createGenerationInsertsRowAndBackfillsAgentTaskId() {
        UUID userId = insertUser("generation@example.test");
        UUID recipeId = UUID.randomUUID();
        AgentGeneratePlanRequest request = request(userId, "req-generation-1", recipeId);
        UUID planId = repository.createProcessing(userId, request, sources(recipeId), "trace-7", json(request));

        repository.createGeneration(planId, "task-abc");

        String agentTaskId = jdbcTemplate.queryForObject(
                "SELECT agent_task_id FROM cooking_plan WHERE id = :planId",
                new MapSqlParameterSource("planId", planId),
                String.class);
        assertThat(agentTaskId).isEqualTo("task-abc");
        CookingPlanRepository.GenerationRow row = repository.findGeneration(planId).orElseThrow();
        assertThat(row.taskId()).isEqualTo("task-abc");
        assertThat(row.syncState()).isEqualTo("PENDING");
        assertThat(row.attemptCount()).isZero();
        assertThat(row.nextPollAt()).isNotNull();
    }

    @Test
    void claimOnlyReturnsDueRowsAndSkipsTerminalPlans() {
        UUID userId = insertUser("claim@example.test");
        UUID duePlanId = createProcessingPlan(userId, "req-claim-due");
        UUID futurePlanId = createProcessingPlan(userId, "req-claim-future");
        repository.createGeneration(duePlanId, "task-due");
        repository.createGeneration(futurePlanId, "task-future");
        // Rewind one generation so only it is due now; push the other into the future.
        jdbcTemplate.update("UPDATE cooking_plan_generation SET next_poll_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE plan_id = :planId",
                new MapSqlParameterSource("planId", duePlanId));
        jdbcTemplate.update("UPDATE cooking_plan_generation SET next_poll_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE plan_id = :planId",
                new MapSqlParameterSource("planId", futurePlanId));

        List<CookingPlanRepository.GenerationClaim> claimed =
                repository.claimDueGenerations(20, Duration.ofSeconds(5));

        assertThat(claimed).singleElement().satisfies(claim -> {
            assertThat(claim.planId()).isEqualTo(duePlanId);
            assertThat(claim.userId()).isEqualTo(userId);
            assertThat(claim.taskId()).isEqualTo("task-due");
            assertThat(claim.attemptCount()).isEqualTo(1);
        });
        // The claimed row is now POLLING and lease-renewed; the future row is untouched.
        CookingPlanRepository.GenerationRow due = repository.findGeneration(duePlanId).orElseThrow();
        assertThat(due.syncState()).isEqualTo("POLLING");
        CookingPlanRepository.GenerationRow future = repository.findGeneration(futurePlanId).orElseThrow();
        assertThat(future.syncState()).isEqualTo("PENDING");
        assertThat(future.attemptCount()).isZero();
    }

    @Test
    void claimSkipsRowsWhosePlanIsNoLongerProcessing() {
        UUID userId = insertUser("claim-terminal@example.test");
        UUID terminalPlanId = createProcessingPlan(userId, "req-claim-terminal");
        repository.createGeneration(terminalPlanId, "task-terminal");
        repository.completeFailed(userId, terminalPlanId, CookingAgentFailureCode.AGENT_INTERNAL_ERROR, null, null);
        jdbcTemplate.update("UPDATE cooking_plan_generation SET next_poll_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE plan_id = :planId",
                new MapSqlParameterSource("planId", terminalPlanId));

        List<CookingPlanRepository.GenerationClaim> claimed =
                repository.claimDueGenerations(20, Duration.ofSeconds(5));

        assertThat(claimed).isEmpty();
    }

    @Test
    void updateGenerationProgressMirrorsProgressAndReArmsLease() {
        UUID userId = insertUser("progress@example.test");
        UUID planId = createProcessingPlan(userId, "req-progress-1");
        repository.createGeneration(planId, "task-progress");
        jdbcTemplate.update("UPDATE cooking_plan_generation SET next_poll_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE plan_id = :planId",
                new MapSqlParameterSource("planId", planId));
        repository.claimDueGenerations(20, Duration.ofSeconds(5));

        repository.updateGenerationProgress(planId, "solve_schedule", 7, "solving", Duration.ofSeconds(30));

        CookingPlanRepository.GenerationRow row = repository.findGeneration(planId).orElseThrow();
        assertThat(row.syncState()).isEqualTo("PENDING");
        assertThat(row.lastProgressNode()).isEqualTo("solve_schedule");
        assertThat(row.lastProgressSteps()).isEqualTo(7);
        assertThat(row.lastProgressMessage()).isEqualTo("solving");
        assertThat(row.nextPollAt()).isAfter(java.time.OffsetDateTime.now());
    }

    @Test
    void completeGenerationMovesToTerminalStateAndStopsClaiming() {
        UUID userId = insertUser("complete@example.test");
        UUID planId = createProcessingPlan(userId, "req-complete-1");
        repository.createGeneration(planId, "task-complete");
        jdbcTemplate.update("UPDATE cooking_plan_generation SET next_poll_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE plan_id = :planId",
                new MapSqlParameterSource("planId", planId));

        repository.completeGeneration(planId, "SUCCEEDED");

        CookingPlanRepository.GenerationRow row = repository.findGeneration(planId).orElseThrow();
        assertThat(row.syncState()).isEqualTo("SUCCEEDED");
        assertThat(repository.claimDueGenerations(20, Duration.ofSeconds(5))).isEmpty();
    }

    private UUID createProcessingPlan(UUID userId, String requestId) {
        UUID recipeId = UUID.randomUUID();
        AgentGeneratePlanRequest request = request(userId, requestId, recipeId);
        return repository.createProcessing(userId, request, sources(recipeId), "trace-" + requestId, json(request));
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, normalised_email, password_hash, display_name)
                VALUES (:id, :email, :email, 'hash', 'Test User')
                """,
                new MapSqlParameterSource("id", id).addValue("email", email));
        return id;
    }

    private UUID insertItem(String canonicalName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_item (id, canonical_name, default_unit)
                VALUES (:id, :name, 'g')
                """,
                new MapSqlParameterSource("id", id).addValue("name", canonicalName));
        return id;
    }

    private UUID insertLot(UUID itemId, UUID userId, String onHand, String reserved) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_lot (id, item_id, user_id, on_hand, reserved, unit, expiry_date)
                VALUES (:id, :itemId, :userId, :onHand, :reserved, 'g', :expiry)
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("itemId", itemId)
                        .addValue("userId", userId)
                        .addValue("onHand", new BigDecimal(onHand))
                        .addValue("reserved", new BigDecimal(reserved))
                        .addValue("expiry", LocalDate.of(2026, 8, 10)));
        return id;
    }

    private AgentGeneratePlanRequest request(UUID userId, String requestId, UUID recipeId) {
        return new AgentGeneratePlanRequest(
                requestId,
                userId.toString(),
                sources(recipeId),
                List.of("VEGAN"),
                List.of(),
                60,
                LocalDate.of(2026, 8, 2),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "1.0",
                null,
                "SG",
                List.of());
    }

    private List<AgentRecipeInput> sources(UUID recipeId) {
        return List.of(new AgentRecipeInput(
                recipeId.toString(),
                "Tofu Bowl\nFirm tofu: 300 g\n1. Pan-fry the tofu.\n2. Serve.",
                new BigDecimal("2")));
    }

    private AgentReadyPlanResponse readyResponse(String planId, UUID lotId) {
        return new AgentReadyPlanResponse(
                planId, "READY", "OPTIMAL", 54,
                List.of(new AgentTimelineTask("t-1", 0, 6, 6, "Pan-fry the tofu.", "d-1", "ACTIVE",
                        "preparation", "MEDIUM", List.of("stove"), null, null)),
                lotId == null
                        ? List.of()
                        : List.of(new AgentCompletionItem("c-1", "chilli", List.of("d-1"),
                                List.of(new AgentLotAllocation(lotId.toString(), new BigDecimal("30.000"), "g")))),
                List.of(new AgentMiseEnPlaceItem("dice: chicken breast", "chicken breast", "dice", 6,
                        List.of("knife"), "diced_chicken")),
                List.of(new AgentDishCompletion("d-1", 54, 9, false)),
                null,
                "explanation",
                "deterministic",
                List.of());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise test payload.", exception);
        }
    }
}
