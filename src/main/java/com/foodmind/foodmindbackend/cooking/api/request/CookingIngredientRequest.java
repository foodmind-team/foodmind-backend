package com.foodmind.foodmindbackend.cooking.api.request;

import com.foodmind.foodmindbackend.cooking.domain.CookingInputSource;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanInput;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingIngredientRequest(
        @Size(min = 1, max = 160)
        String ingredientName,
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal quantity,
        @Size(min = 1, max = 40)
        String unit,
        CookingInputSource source) {

    public CookingPlanInput toDomain() {
        return new CookingPlanInput(ingredientName, quantity, unit, source);
    }
}
