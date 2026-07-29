package com.foodmind.foodmindbackend.record.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record HistoryResult(
        OffsetDateTime fromUtcInclusive,
        OffsetDateTime toUtcExclusive,
        HistoryPeriod period,
        Set<HistorySourceType> types,
        String timeZone,
        List<HistoryBucket> buckets,
        List<HistoryEntry> entries,
        String nextCursor) {
}
