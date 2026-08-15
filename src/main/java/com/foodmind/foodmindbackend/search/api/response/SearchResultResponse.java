package com.foodmind.foodmindbackend.search.api.response;

import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record SearchResultResponse(
        String sourceType,
        UUID sourceId,
        String title,
        String subtitle,
        String snippet,
        String imageReference,
        BigDecimal relevance,
        String visibility,
        UUID ownerUserId,
        UUID groupId,
        OffsetDateTime occurredAt) {

    public static SearchResultResponse from(SearchDocument document, String imageReference) {
        return new SearchResultResponse(
                document.sourceType().name(),
                document.sourceId(),
                document.title(),
                document.subtitle(),
                document.snippet(),
                imageReference,
                document.relevance(),
                document.visibility(),
                document.ownerUserId(),
                document.groupId(),
                document.occurredAt());
    }
}
