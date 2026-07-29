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

public record DrinkRecordFilter(
        OffsetDateTime from,
        OffsetDateTime to,
        UUID placeId,
        FoodRecordVisibility visibility,
        UUID groupId,
        BigDecimal minRating,
        BigDecimal maxRating,
        String sort,
        int page,
        int size) {
}
