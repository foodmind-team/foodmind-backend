package com.foodmind.foodmindbackend.recipe.importing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRecipeImportRequest(
        @NotBlank @Size(max = 100_000) String text) {
}
