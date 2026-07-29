package com.foodmind.foodmindbackend.record.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record HistoryEntry(
        HistorySourceType sourceType,
        UUID sourceId,
        OffsetDateTime occurredAt,
        LocalDate localBucketStart,
        String title,
        String context,
        UUID groupId,
        UUID cuisineId,
        UUID placeId,
        BigDecimal rating,
        Boolean repeatIntent) {
}
