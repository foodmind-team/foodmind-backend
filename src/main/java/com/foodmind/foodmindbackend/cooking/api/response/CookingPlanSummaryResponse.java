package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanSummaryResponse(
        UUID planId,
        String status,
        UUID sourceRecipeId,
        int inputCount,
        int stepCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {

    public static CookingPlanSummaryResponse from(CookingPlanSummary summary) {
        return new CookingPlanSummaryResponse(
                summary.planId(),
                summary.status(),
                summary.sourceRecipeId(),
                summary.inputCount(),
                summary.stepCount(),
                summary.createdAt(),
                summary.completedAt());
    }
}
