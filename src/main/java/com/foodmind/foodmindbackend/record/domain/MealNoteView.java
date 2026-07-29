package com.foodmind.foodmindbackend.record.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

public record MealNoteView(
        UUID id,
        UUID mealId,
        String mealName,
        UUID placeId,
        String placeName,
        UUID cuisineId,
        String cuisineCode,
        OffsetDateTime occurredAt,
        BigDecimal rating,
        Boolean wouldEatAgain,
        FoodRecordVisibility visibility,
        UUID groupId) {
}
