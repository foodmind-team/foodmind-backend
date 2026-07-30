package com.foodmind.foodmindbackend.cooking.api.request;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record GenerateCookingPlanRequest(
        @Size(min = 1, max = 30)
        List<@Valid CookingIngredientRequest> ingredients,
        @Min(1)
        @Max(24)
        int servings,
        @Min(1)
        @Max(1440)
        Integer maxMinutes,
        @DecimalMin("0.0")
        BigDecimal maxBudget,
        @Size(min = 3, max = 3)
        String currency,
        List<String> requiredDietaryTagCodes,
        List<String> avoidAllergenCodes) {

    public CookingPlanRequestContext toContext() {
        return new CookingPlanRequestContext(
                ingredients == null ? List.of() : ingredients.stream().map(CookingIngredientRequest::toDomain).toList(),
                servings,
                maxMinutes,
                maxBudget,
                currency,
                requiredDietaryTagCodes,
                avoidAllergenCodes);
    }
}
