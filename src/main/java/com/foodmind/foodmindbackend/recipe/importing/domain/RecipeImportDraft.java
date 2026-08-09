package com.foodmind.foodmindbackend.recipe.importing.domain;

import java.util.List;

public record RecipeImportDraft(
        String draftId,
        String name,
        Integer servings,
        List<String> ingredients,
        List<String> steps) {
    public RecipeImportDraft {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public boolean ready() {
        return name != null && !name.isBlank()
                && servings != null && servings >= 1 && servings <= 50
                && !ingredients.isEmpty() && !steps.isEmpty();
    }
}
