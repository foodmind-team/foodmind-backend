package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanIngredient;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanIngredientResponse(
        int sequenceNo,
        String ingredientName,
        BigDecimal quantity,
        String unit,
        String availability) {

    public static CookingPlanIngredientResponse from(CookingPlanIngredient ingredient) {
        return new CookingPlanIngredientResponse(
                ingredient.sequenceNo(),
                ingredient.ingredientName(),
                ingredient.quantity(),
                ingredient.unit(),
                ingredient.availability());
    }
}
