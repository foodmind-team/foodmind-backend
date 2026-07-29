package com.foodmind.foodmindbackend.catalog.domain;

import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record PlaceOffering(
        UUID id,
        String displayName,
        Money price,
        Integer spiceLevel,
        String availabilityNote,
        UUID mealId,
        String mealName,
        String mealType,
        String cuisineCode) {
}
