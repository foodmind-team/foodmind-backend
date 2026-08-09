package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.RecipeIngredientSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.RecipeStepSnapshot;
import org.springframework.stereotype.Component;

/** Builds recipes[].text so the agent's rule-based parser extracts it reliably.
 *
 * The agent parser (extractor.py) only recognises ingredient lines in
 * quantity-first form ("300 g Firm tofu") and treats any non-step line
 * before the first numbered step as an ingredient, so the dish-name line
 * must be excluded via explicit Ingredients:/Steps: section headers.
 */
@Component
public class RecipeTextRenderer {

    public String render(RecipeCandidate candidate) {
        StringBuilder sb = new StringBuilder();
        sb.append(candidate.name()).append('\n');
        // The Agent scales quantities from the recipe's stated serving basis.
        // Without this line its rule parser defaults to two servings, which can
        // silently double a four-serving recipe's shopping quantities.
        sb.append("Serves ").append(candidate.defaultServings()).append('\n');
        sb.append("Ingredients:\n");
        for (RecipeIngredientSnapshot ingredient : candidate.ingredients()) {
            if (ingredient.quantity() != null) {
                sb.append(ingredient.quantity().toPlainString());
                if (ingredient.unit() != null) {
                    sb.append(' ').append(ingredient.unit());
                }
                sb.append(' ');
            }
            sb.append(ingredient.ingredientName()).append('\n');
        }
        sb.append("Steps:\n");
        for (RecipeStepSnapshot step : candidate.steps()) {
            sb.append(step.stepNo()).append(". ").append(step.instruction()).append('\n');
        }
        return sb.toString().trim();
    }
}
