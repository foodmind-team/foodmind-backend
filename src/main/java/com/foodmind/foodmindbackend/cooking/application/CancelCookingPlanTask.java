package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cancels an in-flight async cooking-plan task: asks the agent to cancel, then
 * materialises the plan as FAILED(TASK_CANCELLED) and marks the generation
 * CANCELLED — no dirty data survives. A failed cancel call still lands FAILED
 * with the protocol failure code (e.g. AGENT_TASK_NOT_FOUND).
 */
@Service
public class CancelCookingPlanTask {

    private final CookingPlanRepository planRepository;
    private final CookingAgentPort cookingAgentPort;

    public CancelCookingPlanTask(CookingPlanRepository planRepository, CookingAgentPort cookingAgentPort) {
        this.planRepository = planRepository;
        this.cookingAgentPort = cookingAgentPort;
    }

    public CookingPlanResult handle(UUID userId, UUID planId) {
        CookingPlanResult plan = planRepository.findOwned(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PROCESSING".equals(plan.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "The plan is not processing.");
        }
        CookingPlanRepository.GenerationRow generation = planRepository.findGeneration(planId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "The plan is not an async task."));
        CookingAgentFailureCode code;
        try {
            cookingAgentPort.cancelTask(generation.taskId());
            code = CookingAgentFailureCode.TASK_CANCELLED;
        } catch (CookingAgentTaskException exception) {
            code = exception.getFailureCode();
        }
        planRepository.completeFailed(userId, planId, code, null, null);
        planRepository.completeGeneration(planId, "CANCELLED");
        return planRepository.findOwned(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
