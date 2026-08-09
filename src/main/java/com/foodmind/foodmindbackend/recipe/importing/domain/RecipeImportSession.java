package com.foodmind.foodmindbackend.recipe.importing.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecipeImportSession(
        UUID id,
        UUID ownerUserId,
        String sourceText,
        RecipeImportStatus status,
        List<RecipeImportDraft> drafts,
        List<RecipeImportQuestion> questions,
        List<RecipeImportAnswer> answers,
        List<UUID> createdRecipeIds,
        String failureCode,
        String failureMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        long version) {
    public RecipeImportSession {
        drafts = drafts == null ? List.of() : List.copyOf(drafts);
        questions = questions == null ? List.of() : List.copyOf(questions);
        answers = answers == null ? List.of() : List.copyOf(answers);
        createdRecipeIds = createdRecipeIds == null ? List.of() : List.copyOf(createdRecipeIds);
    }
}
