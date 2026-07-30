package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanResponse(
        UUID planId,
        String traceId,
        String status,
        UUID sourceRecipeId,
        String agentContractVersion,
        String fallbackStatus,
        String fallbackVersion,
        String failureCode,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        List<CookingPlanInputResponse> inputs,
        List<CookingPlanIngredientResponse> ingredients,
        List<CookingPlanStepResponse> steps,
        List<CookingPlanWarningResponse> warnings) {

    public static CookingPlanResponse from(CookingPlanResult result) {
        return new CookingPlanResponse(
                result.planId(),
                result.traceId(),
                result.status(),
                result.sourceRecipeId(),
                result.agentContractVersion(),
                result.fallbackStatus(),
                result.fallbackVersion(),
                result.failureCode(),
                result.createdAt(),
                result.completedAt(),
                result.inputs().stream().map(CookingPlanInputResponse::from).toList(),
                result.ingredients().stream().map(CookingPlanIngredientResponse::from).toList(),
                result.steps().stream().map(CookingPlanStepResponse::from).toList(),
                result.warnings().stream().map(CookingPlanWarningResponse::from).toList());
    }
}
