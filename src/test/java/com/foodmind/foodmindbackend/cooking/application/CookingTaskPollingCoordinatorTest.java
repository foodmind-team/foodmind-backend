package com.foodmind.foodmindbackend.cooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanExecution;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskProgress;
import java.util.Map;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskStatus;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSubmission;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException;
import com.foodmind.foodmindbackend.cooking.infrastructure.task.CookingTaskPollingProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit test of the {@link CookingTaskPollingCoordinator} state machine using
 * hand-written fakes (no Spring context, no network).
 */
class CookingTaskPollingCoordinatorTest {

    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String TASK_ID = "task-1";

    private final FakeCookingAgentPort agentPort = new FakeCookingAgentPort();
    private final FakeCookingPlanRepository planRepository = new FakeCookingPlanRepository();
    private final CookingTaskPollingProperties properties = new CookingTaskPollingProperties();
    private final CookingTaskPollingCoordinator coordinator =
            new CookingTaskPollingCoordinator(agentPort, planRepository, properties, new ObjectMapper());

    @BeforeEach
    void setUp() {
        properties.setPollEnabled(true);
        planRepository.claims = List.of(new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 1));
    }

    @Test
    void disabledPollingSkipsClaimAndAgentCalls() {
        properties.setPollEnabled(false);

        coordinator.pollDueTasks();

        assertThat(planRepository.claimCalls).isZero();
        assertThat(agentPort.getTaskCalls).isZero();
    }

    @Test
    void readySnapshotMaterialisesReadyPlanAndSucceedsGeneration() {
        agentPort.snapshots = taskId -> snapshot("READY", readyResultJson(), null);

        coordinator.pollDueTasks();

        assertThat(planRepository.completeReadyPlans).containsExactly(PLAN_ID);
        assertThat(planRepository.generationStates).containsExactly("SUCCEEDED");
        assertThat(planRepository.lastReadyJson).isNotBlank().contains("\"status\":\"READY\"");
    }

    @Test
    void needsConfirmationSnapshotMaterialisesConfirmationPlan() {
        agentPort.snapshots = taskId -> snapshot("NEEDS_CONFIRMATION", """
                {"plan_id":"p-1","status":"NEEDS_CONFIRMATION","assumptions":[],
                 "repair_options":[],"questions":["Would you like to proceed?"],
                 "confirmation_questions":[],"decisions":[],"plan_revision":"r:v1","safety_policy":null}
                """, null);

        coordinator.pollDueTasks();

        assertThat(planRepository.completeConfirmationPlans).containsExactly(PLAN_ID);
        assertThat(planRepository.generationStates).containsExactly("SUCCEEDED");
    }

    @Test
    void infeasibleSnapshotMaterialisesInfeasiblePlan() {
        agentPort.snapshots = taskId -> snapshot("INFEASIBLE", """
                {"plan_id":"p-1","status":"INFEASIBLE",
                 "reasons":["Insufficient 'chilli': need 60 g, have 30 g"],"safe_alternatives":[]}
                """, null);

        coordinator.pollDueTasks();

        assertThat(planRepository.completeInfeasiblePlans).containsExactly(PLAN_ID);
        assertThat(planRepository.generationStates).containsExactly("SUCCEEDED");
    }

    @Test
    void failedSnapshotMaterialisesFailedPlanWithMappedErrorCode() {
        agentPort.snapshots = taskId -> snapshot("FAILED", null, """
                {"status":"FAILED","error_code":"SCHEDULE_UNKNOWN","correlation_id":"c-1","message":"timeout"}
                """);

        coordinator.pollDueTasks();

        assertThat(planRepository.completeFailedPlans).containsExactly(PLAN_ID);
        assertThat(planRepository.lastFailedCode).isEqualTo(CookingAgentFailureCode.SCHEDULE_UNKNOWN);
        assertThat(planRepository.generationStates).containsExactly("FAILED");
    }

    @Test
    void cancelledSnapshotMaterialisesTaskCancelled() {
        agentPort.snapshots = taskId -> snapshot("CANCELLED", null, null);

        coordinator.pollDueTasks();

        assertThat(planRepository.lastFailedCode).isEqualTo(CookingAgentFailureCode.TASK_CANCELLED);
        assertThat(planRepository.generationStates).containsExactly("CANCELLED");
    }

    @Test
    void expiredSnapshotMaterialisesTaskExpiredAsFailed() {
        agentPort.snapshots = taskId -> snapshot("EXPIRED", null, null);

        coordinator.pollDueTasks();

        assertThat(planRepository.lastFailedCode).isEqualTo(CookingAgentFailureCode.TASK_EXPIRED);
        assertThat(planRepository.generationStates).containsExactly("FAILED");
    }

    @Test
    void queuedAndRunningSnapshotsOnlyRenewLeaseWithProgress() {
        agentPort.snapshots = taskId -> snapshot("RUNNING",
                null, null,
                new AgentTaskProgress("solve_schedule", 7, "solving"));

        coordinator.pollDueTasks();

        assertThat(planRepository.progressUpdates).hasSize(1);
        assertThat(planRepository.lastProgressNode).isEqualTo("solve_schedule");
        assertThat(planRepository.lastProgressSteps).isEqualTo(7);
        assertThat(planRepository.lastProgressMessage).isEqualTo("solving");
        assertThat(planRepository.lastProgressDelay).isEqualTo(properties.getPollInterval());
        assertThat(planRepository.generationStates).isEmpty();
        assertThat(planRepository.completeReadyPlans).isEmpty();
    }

    @Test
    void unparseableResultFailsWithSchemaMismatch() {
        agentPort.snapshots = taskId -> snapshot("READY", "{not-json", null);

        coordinator.pollDueTasks();

        assertThat(planRepository.lastFailedCode).isEqualTo(CookingAgentFailureCode.SCHEMA_MISMATCH);
        assertThat(planRepository.generationStates).containsExactly("FAILED");
    }

    @Test
    void pollingFailureBacksOffExponentiallyUntilMaxAttempts() {
        agentPort.failures = taskId -> new CookingAgentTaskException(CookingAgentFailureCode.TIMEOUT);
        planRepository.claims = List.of(
                new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 1),
                new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 2),
                new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 3),
                new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 4));

        coordinator.pollDueTasks();

        // Attempts 1..4 back off (2s, 4s, 8s, 16s capped at max backoff) without failing the plan.
        assertThat(planRepository.progressUpdates).hasSize(4);
        assertThat(planRepository.progressUpdates.get(0).delay).isEqualTo(Duration.ofSeconds(2));
        assertThat(planRepository.progressUpdates.get(1).delay).isEqualTo(Duration.ofSeconds(4));
        assertThat(planRepository.progressUpdates.get(2).delay).isEqualTo(Duration.ofSeconds(8));
        assertThat(planRepository.progressUpdates.get(3).delay).isEqualTo(Duration.ofSeconds(16));
        assertThat(planRepository.completeFailedPlans).isEmpty();

        // The fifth attempt reaches max-attempts and fails the plan.
        planRepository.claims = List.of(new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 5));
        coordinator.pollDueTasks();

        assertThat(planRepository.completeFailedPlans).containsExactly(PLAN_ID);
        assertThat(planRepository.lastFailedCode).isEqualTo(CookingAgentFailureCode.TIMEOUT);
        assertThat(planRepository.generationStates).containsExactly("FAILED");
    }

    @Test
    void backoffIsCappedAtMaxBackoff() {
        agentPort.failures = taskId -> new CookingAgentTaskException(CookingAgentFailureCode.CONNECTION_ERROR);
        properties.setMaxBackoff(Duration.ofSeconds(10));
        planRepository.claims = List.of(new CookingPlanRepository.GenerationClaim(PLAN_ID, USER_ID, TASK_ID, 4));

        coordinator.pollDueTasks();

        assertThat(planRepository.lastProgressDelay).isEqualTo(Duration.ofSeconds(10));
    }

    private static AgentTaskSnapshot snapshot(String status, String resultJson, String errorJson) {
        return snapshot(status, resultJson, errorJson, null);
    }

    private static AgentTaskSnapshot snapshot(String status, String resultJson, String errorJson, AgentTaskProgress progress) {
        return new AgentTaskSnapshot(TASK_ID, AgentTaskStatus.valueOf(status), "req-1",
                "/internal/v2/cooking-plan/tasks/" + TASK_ID, progress, resultJson, errorJson);
    }

    private static String readyResultJson() {
        return """
                {"plan_id":"p-1","status":"READY","solver_status":"OPTIMAL","makespan_minutes":54,
                 "timeline":[],"completion_checklist":[],"mise_en_place":[],"dish_completions":[]}
                """;
    }

    private static class FakeCookingAgentPort implements CookingAgentPort {

        Function<String, AgentTaskSnapshot> snapshots = taskId -> snapshot("RUNNING", null, null);
        Function<String, CookingAgentTaskException> failures;
        int getTaskCalls;

        @Override
        public CookingAgentResult generate(AgentGeneratePlanRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentTaskSubmission submitTask(AgentGeneratePlanRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentTaskSnapshot getTask(String taskId) {
            getTaskCalls++;
            if (failures != null) {
                throw failures.apply(taskId);
            }
            return snapshots.apply(taskId);
        }

        @Override
        public AgentTaskSnapshot cancelTask(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> preprocess(List<AgentRecipeInput> recipes) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeCookingPlanRepository implements CookingPlanRepository {

        List<GenerationClaim> claims = List.of();
        int claimCalls;
        final List<UUID> completeReadyPlans = new ArrayList<>();
        final List<UUID> completeConfirmationPlans = new ArrayList<>();
        final List<UUID> completeInfeasiblePlans = new ArrayList<>();
        final List<UUID> completeFailedPlans = new ArrayList<>();
        final List<String> generationStates = new ArrayList<>();
        final List<ProgressUpdate> progressUpdates = new ArrayList<>();
        String lastReadyJson;
        CookingAgentFailureCode lastFailedCode;
        String lastProgressNode;
        int lastProgressSteps;
        String lastProgressMessage;
        Duration lastProgressDelay;

        @Override
        public UUID createProcessing(UUID userId, AgentGeneratePlanRequest request, List<AgentRecipeInput> sources,
                String traceId, String rawRequestJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID createProcessingChild(UUID userId, AgentGeneratePlanRequest request,
                List<AgentRecipeInput> sources, String traceId, String rawRequestJson,
                UUID parentPlanId, UUID rootPlanId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeReady(UUID userId, UUID planId, AgentReadyPlanResponse response, String rawResponseJson) {
            completeReadyPlans.add(planId);
            lastReadyJson = rawResponseJson;
        }

        @Override
        public void completeConfirmation(UUID userId, UUID planId, AgentConfirmationPlanResponse response, String rawResponseJson) {
            completeConfirmationPlans.add(planId);
        }

        @Override
        public void completeInfeasible(UUID userId, UUID planId, AgentInfeasiblePlanResponse response, String rawResponseJson) {
            completeInfeasiblePlans.add(planId);
        }

        @Override
        public void completeFailed(UUID userId, UUID planId, CookingAgentFailureCode code, AgentFailedPlanResponse response,
                String rawResponseJson) {
            completeFailedPlans.add(planId);
            lastFailedCode = code;
        }

        @Override
        public Optional<CookingPlanResult> findOwned(UUID userId, UUID planId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> findRequestContext(UUID userId, UUID planId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PlanLineage> findLineage(UUID userId, UUID planId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CookingPlanSummary> findOwnedPage(UUID userId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countOwned(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CookingPlanSummary> findSavedPage(UUID userId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countSaved(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookingPlanExecution> findExecution(UUID userId, UUID planId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSaved(UUID userId, UUID planId, boolean saved, boolean resetProgress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateExecutionStep(UUID userId, UUID planId, String stepId, String status, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetExecution(UUID userId, UUID planId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createGeneration(UUID planId, String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GenerationClaim> claimDueGenerations(int batch, Duration pollInterval) {
            claimCalls++;
            return claims;
        }

        @Override
        public void updateGenerationProgress(UUID planId, String node, int completedSteps, String message, Duration nextDelay) {
            progressUpdates.add(new ProgressUpdate(nextDelay));
            lastProgressNode = node;
            lastProgressSteps = completedSteps;
            lastProgressMessage = message;
            lastProgressDelay = nextDelay;
        }

        @Override
        public void completeGeneration(UUID planId, String syncState) {
            generationStates.add(syncState);
        }

        @Override
        public Optional<GenerationRow> findGeneration(UUID planId) {
            throw new UnsupportedOperationException();
        }
    }

    private record ProgressUpdate(Duration delay) {
    }
}
