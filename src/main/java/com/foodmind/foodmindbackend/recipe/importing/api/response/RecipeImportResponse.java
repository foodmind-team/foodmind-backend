package com.foodmind.foodmindbackend.recipe.importing.api.response;

import com.foodmind.foodmindbackend.recipe.api.response.UserRecipeResponse;
import com.foodmind.foodmindbackend.recipe.importing.application.RecipeImportView;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportQuestion;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecipeImportResponse(
        UUID importId,
        String text,
        RecipeImportStatus status,
        List<RecipeImportDraft> drafts,
        List<RecipeImportQuestion> questions,
        List<RecipeImportAnswer> answers,
        List<UserRecipeResponse> createdRecipes,
        String failureCode,
        String failureMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        long version) {
    public static RecipeImportResponse from(RecipeImportView view) {
        var session = view.session();
        return new RecipeImportResponse(
                session.id(),
                session.sourceText(),
                session.status(),
                session.drafts(),
                session.questions(),
                session.answers(),
                view.createdRecipes().stream().map(UserRecipeResponse::from).toList(),
                session.failureCode(),
                session.failureMessage(),
                session.createdAt(),
                session.updatedAt(),
                session.completedAt(),
                session.version());
    }
}
