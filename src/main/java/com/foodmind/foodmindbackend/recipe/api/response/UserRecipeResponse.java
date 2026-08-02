package com.foodmind.foodmindbackend.recipe.api.response;

import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserRecipeResponse(UUID id, String name, int servings, String imageUrl, List<String> tags,
                                 List<String> allergenHints, List<String> ingredients, List<String> steps,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {
    public static UserRecipeResponse from(UserRecipe recipe) {
        return new UserRecipeResponse(recipe.id(), recipe.name(), recipe.servings(), recipe.imageUrl(), recipe.tags(), recipe.allergenHints(),
                recipe.ingredients(), recipe.steps(), recipe.createdAt(), recipe.updatedAt(), recipe.version());
    }
}
