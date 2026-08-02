package com.foodmind.foodmindbackend.recipe.application.port;

import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipePage;
import java.util.Optional;
import java.util.UUID;

public interface UserRecipeRepository {
    UserRecipe create(UserRecipe recipe);
    Optional<UserRecipe> findOwned(UUID ownerUserId, UUID recipeId);
    UserRecipePage findOwnedPage(UUID ownerUserId, int page, int size);
    Optional<UserRecipe> update(UserRecipe recipe, long expectedVersion);
    boolean deleteOwned(UUID ownerUserId, UUID recipeId);
}
