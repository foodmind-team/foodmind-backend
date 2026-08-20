package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanExecution;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Coordinates owner-only saved state and cross-client execution progress. */
@Service
public class ManageCookingPlanExecution {

    private final CookingPlanRepository plans;

    public ManageCookingPlanExecution(CookingPlanRepository plans) {
        this.plans = plans;
    }

    public CookingPlanExecution get(UUID userId, UUID planId) {
        return plans.findExecution(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public CookingPlanExecution save(UUID userId, UUID planId) {
        plans.setSaved(userId, planId, true, false);
        return get(userId, planId);
    }

    public CookingPlanExecution remove(UUID userId, UUID planId, boolean resetProgress) {
        plans.setSaved(userId, planId, false, resetProgress);
        return get(userId, planId);
    }

    public CookingPlanExecution updateStep(
            UUID userId,
            UUID planId,
            String stepId,
            String status,
            long expectedVersion) {
        plans.updateExecutionStep(userId, planId, stepId, status, expectedVersion);
        return get(userId, planId);
    }

    public CookingPlanExecution reset(UUID userId, UUID planId, long expectedVersion) {
        plans.resetExecution(userId, planId, expectedVersion);
        return get(userId, planId);
    }
}
