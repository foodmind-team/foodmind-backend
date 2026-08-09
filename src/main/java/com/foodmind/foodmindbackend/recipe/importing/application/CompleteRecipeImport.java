package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.application.CreateUserRecipe;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteRecipeImport {
    private final RecipeImportRepository repository;
    private final CreateUserRecipe createRecipe;
    private final RecipeImportViews views;
    private final Clock clock;

    public CompleteRecipeImport(
            RecipeImportRepository repository,
            CreateUserRecipe createRecipe,
            RecipeImportViews views,
            Clock clock) {
        this.repository = repository;
        this.createRecipe = createRecipe;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public RecipeImportView handle(UUID ownerUserId, UUID importId, long expectedVersion) {
        RecipeImportSession session = repository.lockOwned(ownerUserId, importId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (session.status() == RecipeImportStatus.COMPLETED) {
            return views.from(session);
        }
        if (session.version() != expectedVersion) {
            throw RecipeImportSupport.conflict();
        }
        if (session.status() != RecipeImportStatus.READY
                || !session.questions().isEmpty()
                || session.drafts().isEmpty()
                || session.drafts().stream().anyMatch(draft -> !draft.ready())) {
            throw new ApiException(ErrorCode.CONFLICT, "Answer every follow-up question before saving recipes.");
        }

        List<UserRecipe> recipes = new ArrayList<>();
        for (RecipeImportDraft draft : session.drafts()) {
            recipes.add(createRecipe.handle(ownerUserId, new CreateUserRecipe.Command(
                    draft.name(),
                    draft.servings(),
                    null,
                    List.of(),
                    List.of(),
                    draft.ingredients(),
                    draft.steps())));
        }
        OffsetDateTime completedAt = OffsetDateTime.now(clock);
        RecipeImportSession completed = repository.markCompleted(
                        ownerUserId,
                        importId,
                        expectedVersion,
                        recipes.stream().map(UserRecipe::id).toList(),
                        completedAt)
                .orElseThrow(RecipeImportSupport::conflict);
        return new RecipeImportView(completed, recipes);
    }
}
