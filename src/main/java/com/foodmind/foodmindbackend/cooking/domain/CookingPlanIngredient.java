package com.foodmind.foodmindbackend.cooking.domain;

import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanIngredient(
        int sequenceNo,
        String ingredientName,
        BigDecimal quantity,
        String unit,
        String availability) {
}
