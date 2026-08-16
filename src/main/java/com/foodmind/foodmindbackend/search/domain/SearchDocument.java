package com.foodmind.foodmindbackend.search.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record SearchDocument(
        SearchSourceType sourceType,
        UUID sourceId,
        UUID ownerUserId,
        UUID groupId,
        String visibility,
        String title,
        String subtitle,
        String snippet,
        UUID mediaAssetId,
        String imageReference,
        OffsetDateTime occurredAt,
        OffsetDateTime sortAt,
        BigDecimal relevance) {

    public SearchDocument withMediaAssetId(UUID value) {
        return new SearchDocument(sourceType, sourceId, ownerUserId, groupId, visibility, title, subtitle, snippet,
                value, imageReference, occurredAt, sortAt, relevance);
    }
}
