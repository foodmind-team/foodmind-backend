package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecipeImportViews {
    private final UserRecipeRepository recipeRepository;

    public RecipeImportViews(UserRecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public RecipeImportView from(RecipeImportSession session) {
        List<UserRecipe> recipes = session.createdRecipeIds().stream()
                .map(recipeId -> recipeRepository.findOwned(session.ownerUserId(), recipeId)
                        .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR)))
                .toList();
        return new RecipeImportView(session, recipes);
    }
}
