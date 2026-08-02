package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.math.BigDecimal;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's RecipeInput (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentRecipeInput(
        String recipeId,
        String text,
        BigDecimal targetServings) {
}
