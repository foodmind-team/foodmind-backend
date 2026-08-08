package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads the async task handle of an in-flight cooking plan. Terminal plans are
 * not served here (404): clients switch to {@code GET /cooking-plans/{planId}}.
 */
@Service
public class GetCookingPlanTask {

    private final CookingPlanRepository planRepository;

    public GetCookingPlanTask(CookingPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public CookingPlanRepository.GenerationRow handle(UUID userId, UUID planId) {
        CookingPlanResult plan = planRepository.findOwned(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PROCESSING".equals(plan.status())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return planRepository.findGeneration(planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
