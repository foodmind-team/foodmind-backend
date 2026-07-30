package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingAgentGenerationResult(
        boolean successful,
        CookingAgentFailureCode failureCode,
        String contractVersion,
        UUID requestId,
        UUID planId,
        String traceId,
        String agentTraceId,
        String status,
        UUID sourceRecipeId,
        int servings,
        Integer totalMinutes,
        BigDecimal estimatedCost,
        String currency,
        List<CookingAgentIngredientResult> ingredients,
        List<CookingAgentStepResult> steps,
        List<CookingAgentWarningResult> warnings) {

    public CookingAgentGenerationResult {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static CookingAgentGenerationResult success(
            String contractVersion,
            UUID requestId,
            UUID planId,
            String traceId,
            String agentTraceId,
            UUID sourceRecipeId,
            int servings,
            Integer totalMinutes,
            BigDecimal estimatedCost,
            String currency,
            List<CookingAgentIngredientResult> ingredients,
            List<CookingAgentStepResult> steps,
            List<CookingAgentWarningResult> warnings) {
        return new CookingAgentGenerationResult(
                true,
                null,
                contractVersion,
                requestId,
                planId,
                traceId,
                agentTraceId,
                "SUCCEEDED",
                sourceRecipeId,
                servings,
                totalMinutes,
                estimatedCost,
                currency,
                ingredients,
                steps,
                warnings);
    }

    public static CookingAgentGenerationResult failure(
            CookingAgentFailureCode failureCode,
            String contractVersion,
            UUID requestId,
            UUID planId,
            String traceId,
            String agentTraceId) {
        return new CookingAgentGenerationResult(
                false,
                failureCode,
                contractVersion,
                requestId,
                planId,
                traceId,
                agentTraceId,
                "FAILED",
                null,
                0,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
    }
}
