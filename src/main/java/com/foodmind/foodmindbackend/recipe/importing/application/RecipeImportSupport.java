package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import java.util.UUID;

final class RecipeImportSupport {
    private RecipeImportSupport() {
    }

    static RecipeImportSession owned(
            RecipeImportRepository repository,
            UUID ownerUserId,
            UUID importId) {
        return repository.findOwned(ownerUserId, importId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    static ApiException conflict() {
        return new ApiException(ErrorCode.CONFLICT, "The recipe import changed. Reload it and try again.");
    }
}
