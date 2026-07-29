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

public record FoodRecord(
        UUID id,
        UUID ownerUserId,
        UUID mealId,
        String mealNameSnapshot,
        UUID placeId,
        String placeNameSnapshot,
        UUID cuisineId,
        String cuisineCode,
        String cuisineName,
        OffsetDateTime occurredAt,
        BigDecimal price,
        String currency,
        BigDecimal rating,
        String comment,
        Boolean wouldEatAgain,
        FoodRecordVisibility visibility,
        UUID groupId,
        UUID mediaAssetId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version) {
}
