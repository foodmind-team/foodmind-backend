package com.foodmind.foodmindbackend.wanttotry.api.response;

import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryItem;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySourceSummary;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record WantToTryResponse(
        UUID id,
        String sourceType,
        UUID sourceId,
        String note,
        OffsetDateTime createdAt,
        boolean sourceAvailable,
        SourceSummaryResponse source) {

    public static WantToTryResponse from(WantToTryItem item) {
        return new WantToTryResponse(
                item.id(),
                item.source().type().name(),
                item.source().id(),
                item.note(),
                item.createdAt(),
                item.sourceAvailable(),
                item.sourceSummary() == null ? null : SourceSummaryResponse.from(item.sourceSummary()));
    }

    public record SourceSummaryResponse(
            String title,
            String subtitle,
            String snippet,
            String imageReference,
            String visibility,
            UUID ownerUserId,
            UUID groupId,
            OffsetDateTime occurredAt) {

        static SourceSummaryResponse from(WantToTrySourceSummary summary) {
            return new SourceSummaryResponse(
                    summary.title(),
                    summary.subtitle(),
                    summary.snippet(),
                    summary.imageReference(),
                    summary.visibility(),
                    summary.ownerUserId(),
                    summary.groupId(),
                    summary.occurredAt());
        }
    }
}
