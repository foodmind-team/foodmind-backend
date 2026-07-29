package com.foodmind.foodmindbackend.catalog.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record RecipeCandidate(
        UUID id,
        String name,
        String description,
        String cuisineCode,
        int defaultServings,
        int prepMinutes,
        int cookMinutes,
        Money estimatedCost,
        List<String> dietaryTagCodes,
        List<String> allergenCodes,
        List<IngredientLine> ingredients,
        List<StepLine> steps) {

    public RecipeCandidate {
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
        ingredients = List.copyOf(ingredients);
        steps = List.copyOf(steps);
    }

    public record IngredientLine(int sequenceNo, UUID ingredientId, String canonicalName, BigDecimal quantity, String unit, boolean optional) {
    }

    public record StepLine(int stepNo, String instruction) {
    }
}
