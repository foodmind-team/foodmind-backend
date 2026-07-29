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

public record FoodRecordFilter(
        OffsetDateTime from,
        OffsetDateTime to,
        UUID cuisineId,
        UUID mealId,
        UUID placeId,
        FoodRecordVisibility visibility,
        UUID groupId,
        BigDecimal minRating,
        BigDecimal maxRating,
        String sort,
        int page,
        int size) {
}
