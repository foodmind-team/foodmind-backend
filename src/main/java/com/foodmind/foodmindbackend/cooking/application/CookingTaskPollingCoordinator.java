package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailureCodeMapper;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskProgress;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskStatus;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException;
import com.foodmind.foodmindbackend.cooking.infrastructure.task.CookingTaskPollingProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Background coordinator that mirrors async cooking-agent task state onto the
 * cooking_plan root, reusing the synchronous terminal-state materialisation
 * (completeReady/completeConfirmation/completeInfeasible/completeFailed, all
 * guarded by {@code status='PROCESSING'} so re-entry is naturally idempotent).
 *
 * <p>Claims due generations with {@code FOR UPDATE SKIP LOCKED} (lease window =
 * poll interval; expired POLLING rows are re-claimed after a restart), polls the
 * agent task, and maps the task state per the design matrix (§5.3).
 */
@Component
public class CookingTaskPollingCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CookingTaskPollingCoordinator.class);

    private final CookingAgentPort cookingAgentPort;
    private final CookingPlanRepository planRepository;
    private final CookingTaskPollingProperties properties;
    private final ObjectMapper objectMapper;

    public CookingTaskPollingCoordinator(
            CookingAgentPort cookingAgentPort,
            CookingPlanRepository planRepository,
            CookingTaskPollingProperties properties,
            ObjectMapper objectMapper) {
        this.cookingAgentPort = cookingAgentPort;
        this.planRepository = planRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${foodmind.cooking.task.poll-interval:2s}")
    public void pollDueTasks() {
        if (!properties.isPollEnabled()) {
            return;
        }
        List<CookingPlanRepository.GenerationClaim> claims = planRepository.claimDueGenerations(
                properties.getPollBatch(), properties.getPollInterval());
        for (CookingPlanRepository.GenerationClaim claim : claims) {
            pollClaim(claim);
        }
    }

    private void pollClaim(CookingPlanRepository.GenerationClaim claim) {
        try {
            AgentTaskSnapshot snapshot = cookingAgentPort.getTask(claim.taskId());
            materialize(claim, snapshot);
        } catch (CookingAgentTaskException exception) {
            handlePollingFailure(claim, exception.getFailureCode());
        } catch (RuntimeException exception) {
            LOGGER.warn("cooking_task_poll planId={} taskId={} unexpected error",
                    claim.planId(), claim.taskId(), exception);
            handlePollingFailure(claim, CookingAgentFailureCode.AGENT_INTERNAL_ERROR);
        }
    }

    private void materialize(CookingPlanRepository.GenerationClaim claim, AgentTaskSnapshot snapshot) {
        switch (snapshot.status()) {
            case QUEUED, RUNNING -> renewLease(claim, snapshot);
            case READY, NEEDS_CONFIRMATION, INFEASIBLE -> materializeSuccess(claim, snapshot);
            case FAILED -> materializeFailed(claim, snapshot);
            case CANCELLED -> materializeCancelled(claim, snapshot);
            case EXPIRED -> materializeExpired(claim, snapshot);
        }
    }

    private void renewLease(CookingPlanRepository.GenerationClaim claim, AgentTaskSnapshot snapshot) {
        AgentTaskProgress progress = snapshot.progress();
        planRepository.updateGenerationProgress(claim.planId(),
                progress == null ? null : progress.node(),
                progress == null ? 0 : progress.completedSteps(),
                progress == null ? null : progress.message(),
                properties.getPollInterval());
    }

    private void materializeSuccess(CookingPlanRepository.GenerationClaim claim, AgentTaskSnapshot snapshot) {
        try {
            AgentPlanResponse response = objectMapper.readValue(snapshot.resultJson(), AgentPlanResponse.class);
            switch (response.status()) {
                case "READY" -> planRepository.completeReady(claim.userId(), claim.planId(),
                        (AgentReadyPlanResponse) response, snapshot.resultJson());
                case "NEEDS_CONFIRMATION" -> planRepository.completeConfirmation(claim.userId(), claim.planId(),
                        (AgentConfirmationPlanResponse) response, snapshot.resultJson());
                case "INFEASIBLE" -> planRepository.completeInfeasible(claim.userId(), claim.planId(),
                        (AgentInfeasiblePlanResponse) response, snapshot.resultJson());
                default -> throw new IllegalArgumentException("Unexpected plan status " + response.status());
            }
            planRepository.completeGeneration(claim.planId(), "SUCCEEDED");
        } catch (JacksonException | IllegalArgumentException exception) {
            LOGGER.warn("cooking_task_poll planId={} taskId={} unparseable result",
                    claim.planId(), claim.taskId(), exception);
            planRepository.completeFailed(claim.userId(), claim.planId(), CookingAgentFailureCode.SCHEMA_MISMATCH,
                    null, snapshot.resultJson());
            planRepository.completeGeneration(claim.planId(), "FAILED");
        }
    }

    private void materializeFailed(CookingPlanRepository.GenerationClaim claim, AgentTaskSnapshot snapshot) {
        String errorJson = snapshot.errorJson();
        CookingAgentFailureCode code = errorCode(errorJson);
        planRepository.completeFailed(claim.userId(), claim.planId(), code, parseErrorResponse(errorJson), errorJson);
        planRepository.completeGeneration(claim.planId(), "FAILED");
    }

    private void materializeCancelled(CookingPlanRepository.GenerationClaim claim, AgentTaskSnapshot snapshot) {
        planRepository.completeFailed(claim.userId(), claim.planId(), CookingAgentFailureCode.TASK_CANCELLED,
                parseErrorResponse(snapshot.errorJson()), snapshot.errorJson());
        planRepository.completeGeneration(claim.planId(), "CANCELLED");
    }

    private void materializeExpired(CookingPlanRepository.GenerationClaim claim, AgentTaskSnapshot snapshot) {
        planRepository.completeFailed(claim.userId(), claim.planId(), CookingAgentFailureCode.TASK_EXPIRED,
                parseErrorResponse(snapshot.errorJson()), snapshot.errorJson());
        planRepository.completeGeneration(claim.planId(), "FAILED");
    }

    private void handlePollingFailure(CookingPlanRepository.GenerationClaim claim, CookingAgentFailureCode code) {
        if (claim.attemptCount() >= properties.getMaxAttempts()) {
            LOGGER.warn("cooking_task_poll planId={} taskId={} exceeded max attempts (code={})",
                    claim.planId(), claim.taskId(), code.name());
            planRepository.completeFailed(claim.userId(), claim.planId(), code, null, null);
            planRepository.completeGeneration(claim.planId(), "FAILED");
        } else {
            long backoffSeconds = Math.min(
                    1L << claim.attemptCount(),
                    properties.getMaxBackoff().toSeconds());
            planRepository.updateGenerationProgress(claim.planId(), null, 0, null,
                    Duration.ofSeconds(backoffSeconds));
        }
    }

    private CookingAgentFailureCode errorCode(String errorJson) {
        if (errorJson == null) {
            return CookingAgentFailureCode.AGENT_INTERNAL_ERROR;
        }
        try {
            JsonNode root = objectMapper.readTree(errorJson);
            return AgentFailureCodeMapper.map(root.path("error_code").asText(null));
        } catch (JacksonException exception) {
            return CookingAgentFailureCode.SCHEMA_MISMATCH;
        }
    }

    private AgentFailedPlanResponse parseErrorResponse(String errorJson) {
        if (errorJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(errorJson, AgentFailedPlanResponse.class);
        } catch (JacksonException exception) {
            return null;
        }
    }
}
