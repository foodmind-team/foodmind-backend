package com.foodmind.foodmindbackend.record.api.response;

import com.foodmind.foodmindbackend.record.domain.FoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

public record FoodRecordResponse(
        UUID id,
        UUID mealId,
        String mealNameSnapshot,
        UUID placeId,
        String placeNameSnapshot,
        UUID cuisineId,
        String cuisineCode,
        String cuisineName,
        OffsetDateTime occurredAt,
        MoneyResponse price,
        BigDecimal rating,
        String comment,
        Boolean wouldEatAgain,
        FoodRecordVisibility visibility,
        UUID groupId,
        UUID mediaAssetId,
        String imageUrl,
        boolean canManage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version) {

    public static FoodRecordResponse from(FoodRecord record, String imageUrl, boolean canManage) {
        return new FoodRecordResponse(
                record.id(),
                record.mealId(),
                record.mealNameSnapshot(),
                record.placeId(),
                record.placeNameSnapshot(),
                record.cuisineId(),
                record.cuisineCode(),
                record.cuisineName(),
                record.occurredAt(),
                record.price() == null ? null : new MoneyResponse(record.price(), record.currency()),
                record.rating(),
                record.comment(),
                record.wouldEatAgain(),
                record.visibility(),
                record.groupId(),
                record.mediaAssetId(),
                imageUrl,
                canManage,
                record.createdAt(),
                record.updatedAt(),
                record.version());
    }

    public record MoneyResponse(
            BigDecimal amount,
            String currency) {
    }
}
