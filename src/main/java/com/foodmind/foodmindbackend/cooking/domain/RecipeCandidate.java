package com.foodmind.foodmindbackend.cooking.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record RecipeCandidate(
        UUID recipeId,
        String name,
        String description,
        int defaultServings,
        int totalMinutes,
        BigDecimal estimatedCost,
        String currency,
        List<String> dietaryTagCodes,
        List<String> allergenCodes,
        List<RecipeIngredientSnapshot> ingredients,
        List<RecipeStepSnapshot> steps) {

    public RecipeCandidate {
        dietaryTagCodes = dietaryTagCodes == null ? List.of() : List.copyOf(dietaryTagCodes);
        allergenCodes = allergenCodes == null ? List.of() : List.copyOf(allergenCodes);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
