package com.foodmind.foodmindbackend.recipe.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteUserRecipe {
    private final UserRecipeRepository repository;
    public DeleteUserRecipe(UserRecipeRepository repository) { this.repository = repository; }
    @Transactional
    public void handle(UUID ownerUserId, UUID recipeId) {
        if (!repository.deleteOwned(ownerUserId, recipeId)) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Recipe was not found.");
    }
}
