package com.foodmind.foodmindbackend.record.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record DrinkRecord(
        UUID id,
        UUID ownerUserId,
        String drinkName,
        UUID placeId,
        String shopNameSnapshot,
        OffsetDateTime occurredAt,
        BigDecimal price,
        String currency,
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
}
