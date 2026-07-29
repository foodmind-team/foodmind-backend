package com.foodmind.foodmindbackend.catalog.domain;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record MealDetail(
        UUID id,
        String name,
        String description,
        ReferenceItem cuisine,
        String mealType,
        Integer defaultSpiceLevel,
        List<String> dietaryTagCodes,
        List<String> allergenCodes,
        List<MealOffering> offerings) {

    public MealDetail {
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
        offerings = List.copyOf(offerings);
    }
}
