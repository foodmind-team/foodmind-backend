package com.foodmind.foodmindbackend.recipe.api.request;

import com.foodmind.foodmindbackend.recipe.application.CreateUserRecipe;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserRecipeRequest(
        @NotBlank @Size(max = 160) String name,
        @Min(1) @Max(50) int servings,
        @Size(max = 2048) String imageUrl,
        @Size(max = 20) List<@NotBlank @Size(max = 80) String> tags,
        @Size(max = 20) List<@NotBlank @Size(max = 80) String> allergenHints,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 500) String> ingredients,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 1000) String> steps) {
    public CreateUserRecipe.Command toCommand() {
        return new CreateUserRecipe.Command(name, servings, imageUrl, tags, allergenHints, ingredients, steps);
    }
}
