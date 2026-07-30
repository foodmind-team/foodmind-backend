package com.foodmind.foodmindbackend.cooking.domain;

import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanInput(
        String ingredientName,
        BigDecimal quantity,
        String unit,
        CookingInputSource source) {

    public CookingPlanInput {
        ingredientName = ingredientName == null ? null : ingredientName.trim();
        unit = unit == null ? null : unit.trim();
        source = source == null ? CookingInputSource.MANUAL : source;
    }
}
