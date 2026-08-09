package com.foodmind.foodmindbackend.recipe.importing.application.port;

import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportQuestion;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeImportRepository {
    RecipeImportSession create(RecipeImportSession session);

    Optional<RecipeImportSession> findOwned(UUID ownerUserId, UUID importId);

    Optional<RecipeImportSession> lockOwned(UUID ownerUserId, UUID importId);

    Optional<RecipeImportSession> updateAgentResult(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            RecipeImportStatus status,
            List<RecipeImportDraft> drafts,
            List<RecipeImportQuestion> questions,
            List<RecipeImportAnswer> answers);

    Optional<RecipeImportSession> markFailed(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            String failureCode,
            String failureMessage);

    Optional<RecipeImportSession> markCompleted(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            List<UUID> createdRecipeIds,
            OffsetDateTime completedAt);
}
