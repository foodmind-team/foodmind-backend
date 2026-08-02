package com.foodmind.foodmindbackend.recipe.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserRecipe {
    private final UserRecipeRepository repository;
    public GetUserRecipe(UserRecipeRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public UserRecipe handle(UUID ownerUserId, UUID recipeId) {
        return repository.findOwned(ownerUserId, recipeId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Recipe was not found."));
    }
}
