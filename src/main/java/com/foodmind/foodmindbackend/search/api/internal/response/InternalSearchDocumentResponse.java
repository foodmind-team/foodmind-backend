package com.foodmind.foodmindbackend.search.api.internal.response;

import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record InternalSearchDocumentResponse(
        String sourceType,
        UUID sourceId,
        String title,
        String subtitle,
        String snippet,
        String visibility,
        UUID groupId,
        OffsetDateTime occurredAt) {

    public static InternalSearchDocumentResponse from(SearchDocument document) {
        return new InternalSearchDocumentResponse(
                document.sourceType().name(),
                document.sourceId(),
                document.title(),
                document.subtitle(),
                document.snippet(),
                document.visibility(),
                document.groupId(),
                document.occurredAt());
    }
}
