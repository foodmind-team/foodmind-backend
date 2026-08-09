package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import java.util.List;

public record RecipeImportView(RecipeImportSession session, List<UserRecipe> createdRecipes) {
    public RecipeImportView {
        createdRecipes = createdRecipes == null ? List.of() : List.copyOf(createdRecipes);
    }
}
