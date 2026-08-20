package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanExecution;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists agent-native cooking plans (PROCESSING root, then one of the four
 * terminal states with their materialised child tables) and reads them back.
 */
public interface CookingPlanRepository {

    UUID createProcessing(UUID userId, AgentGeneratePlanRequest request, List<AgentRecipeInput> sources,
                          String traceId, String rawRequestJson);

    default UUID createProcessingWithReuseMetadata(
            UUID userId,
            AgentGeneratePlanRequest request,
            List<AgentRecipeInput> sources,
            String traceId,
            String rawRequestJson,
            String requestFingerprint,
            UUID reusedFromPlanId) {
        return createProcessing(userId, request, sources, traceId, rawRequestJson);
    }

    default UUID createReusedReady(
            UUID userId,
            AgentGeneratePlanRequest request,
            List<AgentRecipeInput> sources,
            String traceId,
            String rawRequestJson,
            String requestFingerprint,
            UUID reusedFromPlanId,
            AgentReadyPlanResponse reusedResponse) {
        UUID planId = createProcessing(userId, request, sources, traceId, rawRequestJson);
        completeReady(userId, planId, reusedResponse, null);
        return planId;
    }

    UUID createProcessingChild(UUID userId, AgentGeneratePlanRequest request, List<AgentRecipeInput> sources,
                               String traceId, String rawRequestJson, UUID parentPlanId, UUID rootPlanId);

    void completeReady(UUID userId, UUID planId, AgentReadyPlanResponse response, String rawResponseJson);

    void completeConfirmation(UUID userId, UUID planId, AgentConfirmationPlanResponse response, String rawResponseJson);

    void completeInfeasible(UUID userId, UUID planId, AgentInfeasiblePlanResponse response, String rawResponseJson);

    void completeFailed(UUID userId, UUID planId, CookingAgentFailureCode code, AgentFailedPlanResponse response,
                        String rawResponseJson);

    Optional<CookingPlanResult> findOwned(UUID userId, UUID planId);

    default Optional<ReusableReadyPlan> findReusableReadyPlan(UUID userId, String requestFingerprint) {
        return Optional.empty();
    }

    /** The stored agent request JSON ({@code request_context}) of an owned plan. */
    Optional<String> findRequestContext(UUID userId, UUID planId);

    Optional<PlanLineage> findLineage(UUID userId, UUID planId);

    List<CookingPlanSummary> findOwnedPage(UUID userId, int page, int size);

    long countOwned(UUID userId);

    List<CookingPlanSummary> findSavedPage(UUID userId, int page, int size);

    long countSaved(UUID userId);

    Optional<CookingPlanExecution> findExecution(UUID userId, UUID planId);

    void setSaved(UUID userId, UUID planId, boolean saved, boolean resetProgress);

    void updateExecutionStep(UUID userId, UUID planId, String stepId, String status, long expectedVersion);

    void resetExecution(UUID userId, UUID planId, long expectedVersion);

    // =========================================================================
    // Async task polling (V15 cooking_plan_generation)
    // =========================================================================

    /**
     * Records an async task submission: inserts the generation row (PENDING,
     * due immediately) and back-fills {@code cooking_plan.agent_task_id}.
     */
    void createGeneration(UUID planId, String taskId);

    /**
     * Claims due generations via a single {@code FOR UPDATE SKIP LOCKED} UPDATE:
     * moves them to POLLING, renews the lease and increments attempt_count.
     * Returns the claimed rows including the owner user id.
     */
    List<GenerationClaim> claimDueGenerations(int batch, Duration pollInterval);

    /**
     * Mirrors task progress and re-arms the row for the next poll (sync_state back
     * to PENDING, next_poll_at = now + delay). Used both for lease renewal of
     * in-flight tasks and exponential backoff of polling failures.
     */
    void updateGenerationProgress(UUID planId, String node, int completedSteps, String message, Duration nextDelay);

    /** Moves a generation row to a terminal sync_state (SUCCEEDED/FAILED/CANCELLED). */
    void completeGeneration(UUID planId, String syncState);

    Optional<GenerationRow> findGeneration(UUID planId);

    record GenerationClaim(UUID planId, UUID userId, String taskId, int attemptCount) {
    }

    record GenerationRow(
            UUID planId,
            String taskId,
            String syncState,
            OffsetDateTime nextPollAt,
            int attemptCount,
            String lastErrorCode,
            String lastProgressNode,
            int lastProgressSteps,
            String lastProgressMessage) {
    }

    record PlanLineage(UUID planId, UUID parentPlanId, UUID rootPlanId) {
    }

    record ReusableReadyPlan(UUID planId, String responseJson, OffsetDateTime finishedAt) {
    }
}
