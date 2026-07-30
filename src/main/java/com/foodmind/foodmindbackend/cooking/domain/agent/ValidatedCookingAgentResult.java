package com.foodmind.foodmindbackend.cooking.domain.agent;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanIngredient;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanStep;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanWarning;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record ValidatedCookingAgentResult(
        String agentContractVersion,
        String agentTraceId,
        UUID sourceRecipeId,
        List<CookingPlanIngredient> ingredients,
        List<CookingPlanStep> steps,
        List<CookingPlanWarning> warnings) {

    public ValidatedCookingAgentResult {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
