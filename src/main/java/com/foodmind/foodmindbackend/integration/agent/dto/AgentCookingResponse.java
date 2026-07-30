package com.foodmind.foodmindbackend.integration.agent.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record AgentCookingResponse(
        String contractVersion,
        UUID requestId,
        UUID planId,
        String traceId,
        String agentTraceId,
        String status,
        UUID sourceRecipeId,
        Integer servings,
        Integer totalMinutes,
        BigDecimal estimatedCost,
        String currency,
        List<AgentCookingIngredientResponse> ingredients,
        List<AgentCookingStepResponse> steps,
        List<AgentCookingWarningResponse> warnings) {
}
