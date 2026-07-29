package com.foodmind.foodmindbackend.record.domain;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record HistoryFilter(
        OffsetDateTime fromUtcInclusive,
        OffsetDateTime toUtcExclusive,
        HistoryPeriod period,
        Set<HistorySourceType> types,
        String timeZone,
        UUID groupId,
        UUID cuisineId,
        UUID placeId,
        HistoryCursor after,
        int size) {
}
