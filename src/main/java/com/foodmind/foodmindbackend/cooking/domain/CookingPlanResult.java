package com.foodmind.foodmindbackend.cooking.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanResult(
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
        List<CookingPlanInput> inputs,
        List<CookingPlanIngredient> ingredients,
        List<CookingPlanStep> steps,
        List<CookingPlanWarning> warnings) {

    public CookingPlanResult {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
