package com.foodmind.foodmindbackend.recipe.importing.domain;

public record RecipeImportQuestion(
        String questionId,
        String draftId,
        String fieldPath,
        String prompt,
        String responseType,
        boolean required,
        String suggestedValue) {
}
