package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanInput;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanInputResponse(
        String ingredientName,
        BigDecimal quantity,
        String unit,
        String source) {

    public static CookingPlanInputResponse from(CookingPlanInput input) {
        return new CookingPlanInputResponse(
                input.ingredientName(),
                input.quantity(),
                input.unit(),
                input.source().name());
    }
}
