package com.foodmind.foodmindbackend.record.api.response;

import com.foodmind.foodmindbackend.record.domain.DrinkRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record DrinkRecordResponse(
        UUID id,
        String drinkName,
        UUID placeId,
        String shopNameSnapshot,
        OffsetDateTime occurredAt,
        MoneyResponse price,
        BigDecimal rating,
        String comment,
        Integer sweetnessLevel,
        Integer iceLevel,
        Boolean wouldBuyAgain,
        FoodRecordVisibility visibility,
        UUID groupId,
        UUID mediaAssetId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version) {

    public static DrinkRecordResponse from(DrinkRecord record) {
        return new DrinkRecordResponse(
                record.id(),
                record.drinkName(),
                record.placeId(),
                record.shopNameSnapshot(),
                record.occurredAt(),
                record.price() == null ? null : new MoneyResponse(record.price(), record.currency()),
                record.rating(),
                record.comment(),
                record.sweetnessLevel(),
                record.iceLevel(),
                record.wouldBuyAgain(),
                record.visibility(),
                record.groupId(),
                record.mediaAssetId(),
                record.createdAt(),
                record.updatedAt(),
                record.version());
    }

    public record MoneyResponse(
            BigDecimal amount,
            String currency) {
    }
}
