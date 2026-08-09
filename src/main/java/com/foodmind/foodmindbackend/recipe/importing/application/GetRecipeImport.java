package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetRecipeImport {
    private final RecipeImportRepository repository;
    private final RecipeImportViews views;

    public GetRecipeImport(RecipeImportRepository repository, RecipeImportViews views) {
        this.repository = repository;
        this.views = views;
    }

    public RecipeImportView handle(UUID ownerUserId, UUID importId) {
        return views.from(RecipeImportSupport.owned(repository, ownerUserId, importId));
    }
}
