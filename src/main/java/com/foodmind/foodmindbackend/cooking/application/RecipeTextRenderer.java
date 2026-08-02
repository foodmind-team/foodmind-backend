package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.RecipeIngredientSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.RecipeStepSnapshot;
import org.springframework.stereotype.Component;

/** Builds recipes[].text from DB rows so the agent runs its native parse pipeline. */
@Component
public class RecipeTextRenderer {

    public String render(RecipeCandidate candidate) {
        StringBuilder sb = new StringBuilder();
        sb.append(candidate.name()).append('\n');
        for (RecipeIngredientSnapshot ingredient : candidate.ingredients()) {
            sb.append(ingredient.ingredientName());
            if (ingredient.quantity() != null) {
                sb.append(": ").append(ingredient.quantity().toPlainString());
                if (ingredient.unit() != null) {
                    sb.append(' ').append(ingredient.unit());
                }
            }
            sb.append('\n');
        }
        for (RecipeStepSnapshot step : candidate.steps()) {
            sb.append(step.stepNo()).append(". ").append(step.instruction()).append('\n');
        }
        return sb.toString().trim();
    }
}
