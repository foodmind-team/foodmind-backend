package com.foodmind.foodmindbackend.cooking.domain.agent;

import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingAgentCandidate(
        UUID recipeId,
        RecipeCandidate recipe,
        Map<String, Object> snapshot) {

    public CookingAgentCandidate {
        snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
    }
}
