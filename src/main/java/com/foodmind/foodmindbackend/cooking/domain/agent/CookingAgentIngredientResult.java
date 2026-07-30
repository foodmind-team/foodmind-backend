package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingAgentIngredientResult(
        int sequenceNo,
        String ingredientName,
        BigDecimal quantity,
        String unit,
        String availability) {
}
