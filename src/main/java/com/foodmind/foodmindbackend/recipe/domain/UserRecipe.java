package com.foodmind.foodmindbackend.recipe.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Owner-scoped recipe authored by an authenticated user. */
public record UserRecipe(
        UUID id,
        UUID ownerUserId,
        String name,
        int servings,
        String imageUrl,
        List<String> tags,
        List<String> allergenHints,
        List<String> ingredients,
        List<String> steps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
    public UserRecipe {
        tags = tags == null ? List.of() : List.copyOf(tags);
        allergenHints = allergenHints == null ? List.of() : List.copyOf(allergenHints);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
