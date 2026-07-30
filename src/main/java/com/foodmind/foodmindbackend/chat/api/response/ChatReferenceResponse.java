package com.foodmind.foodmindbackend.chat.api.response;

import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatReferenceResponse(
        UUID id,
        String origin,
        UUID introducedByMessageId,
        String sourceType,
        UUID sourceId,
        boolean available,
        String title,
        String snippet,
        OffsetDateTime createdAt) {

    public static ChatReferenceResponse from(ChatReference reference) {
        return new ChatReferenceResponse(
                reference.id(),
                reference.origin().name(),
                reference.introducedByMessageId(),
                reference.sourceType().name(),
                reference.sourceId(),
                reference.available(),
                reference.title(),
                reference.snippet(),
                reference.createdAt());
    }
}
