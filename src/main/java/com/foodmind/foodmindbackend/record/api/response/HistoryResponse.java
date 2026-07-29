package com.foodmind.foodmindbackend.record.api.response;

import com.foodmind.foodmindbackend.record.domain.HistoryBucket;
import com.foodmind.foodmindbackend.record.domain.HistoryEntry;
import com.foodmind.foodmindbackend.record.domain.HistoryPeriod;
import com.foodmind.foodmindbackend.record.domain.HistoryResult;
import com.foodmind.foodmindbackend.record.domain.HistorySourceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record HistoryResponse(
        OffsetDateTime fromUtcInclusive,
        OffsetDateTime toUtcExclusive,
        HistoryPeriod period,
        Set<HistorySourceType> types,
        String timeZone,
        List<BucketResponse> buckets,
        List<EntryResponse> entries,
        String nextCursor) {

    public static HistoryResponse from(HistoryResult result) {
        return new HistoryResponse(
                result.fromUtcInclusive(),
                result.toUtcExclusive(),
                result.period(),
                result.types(),
                result.timeZone(),
                result.buckets().stream().map(BucketResponse::from).toList(),
                result.entries().stream().map(EntryResponse::from).toList(),
                result.nextCursor());
    }

    public record BucketResponse(
            LocalDate bucketStart,
            long totalCount,
            long foodCount,
            long drinkCount) {

        static BucketResponse from(HistoryBucket bucket) {
            return new BucketResponse(bucket.bucketStart(), bucket.totalCount(), bucket.foodCount(), bucket.drinkCount());
        }
    }

    public record EntryResponse(
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

        static EntryResponse from(HistoryEntry entry) {
            return new EntryResponse(
                    entry.sourceType(),
                    entry.sourceId(),
                    entry.occurredAt(),
                    entry.localBucketStart(),
                    entry.title(),
                    entry.context(),
                    entry.groupId(),
                    entry.cuisineId(),
                    entry.placeId(),
                    entry.rating(),
                    entry.repeatIntent());
        }
    }
}
