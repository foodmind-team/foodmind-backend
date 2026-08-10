package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportAgentPort;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateRecipeImport {
    private final RecipeImportRepository repository;
    private final RecipeImportAgentPort agent;
    private final RecipeImportViews views;
    private final Clock clock;

    public CreateRecipeImport(
            RecipeImportRepository repository,
            RecipeImportAgentPort agent,
            RecipeImportViews views,
            Clock clock) {
        this.repository = repository;
        this.agent = agent;
        this.views = views;
        this.clock = clock;
    }

    public RecipeImportView handle(UUID ownerUserId, String inputText) {
        String text = RecipeImportInputPolicy.validateText(inputText);
        OffsetDateTime now = OffsetDateTime.now(clock);
        RecipeImportSession created = repository.create(new RecipeImportSession(
                UUID.randomUUID(),
                ownerUserId,
                text,
                RecipeImportStatus.PROCESSING,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                now,
                now,
                null,
                0));
        try {
            RecipeImportAgentPort.Result result = agent.parse(
                    created.id().toString(), text, List.of(), List.of(), List.of());
            RecipeImportAgentResultValidator.validate(result);
            RecipeImportSession updated = repository.updateAgentResult(
                            ownerUserId,
                            created.id(),
                            created.version(),
                            result.status(),
                            result.drafts(),
                            result.questions(),
                            List.of())
                    .orElseThrow(RecipeImportSupport::conflict);
            return views.from(updated);
        } catch (ApiException exception) {
            repository.markFailed(
                    ownerUserId,
                    created.id(),
                    created.version(),
                    "AGENT_UNAVAILABLE",
                    "Recipe parsing is temporarily unavailable. Please try again.");
            throw exception;
        }
    }
}
