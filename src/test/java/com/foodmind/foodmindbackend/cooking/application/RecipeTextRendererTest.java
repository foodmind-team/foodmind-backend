package com.foodmind.foodmindbackend.cooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.RecipeIngredientSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.RecipeStepSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeTextRendererTest {

    private final RecipeTextRenderer renderer = new RecipeTextRenderer();

    @Test
    void rendersCatalogueRecipeFromIngredientsAndSteps() {
        RecipeCandidate candidate = new RecipeCandidate(
                UUID.randomUUID(), "Tofu Bowl", "A simple bowl", 2, 35,
                new BigDecimal("9.50"), "SGD", List.of("VEGETARIAN"), List.of("SOY"),
                List.of(new RecipeIngredientSnapshot(1, "Firm tofu", new BigDecimal("300"), "g", false)),
                List.of(new RecipeStepSnapshot(1, "Pan-fry the tofu."), new RecipeStepSnapshot(2, "Serve.")));

        String text = renderer.render(candidate);

        assertThat(text)
                .contains("Tofu Bowl")
                .contains("Serves 2")
                .contains("Ingredients:")
                .contains("300 g Firm tofu")
                .contains("Steps:")
                .contains("1. Pan-fry the tofu.")
                .contains("2. Serve.");
    }

    @Test
    void rendersOwnerRecipeWithoutQuantities() {
        RecipeCandidate candidate = new RecipeCandidate(
                UUID.randomUUID(), "My Curry", "Owner recipe", 2, 1, null, null,
                List.of(), List.of(),
                List.of(new RecipeIngredientSnapshot(1, "Curry paste", null, null, false)),
                List.of(new RecipeStepSnapshot(1, "Simmer the paste."), new RecipeStepSnapshot(2, "Serve.")));

        String text = renderer.render(candidate);

        assertThat(text)
                .contains("My Curry")
                .contains("Serves 2")
                .contains("Curry paste")
                .contains("Steps:")
                .doesNotContain("Curry paste:");
    }
}
