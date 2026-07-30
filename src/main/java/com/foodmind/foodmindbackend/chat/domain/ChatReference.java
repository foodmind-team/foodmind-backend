package com.foodmind.foodmindbackend.chat.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatReference(
        UUID id,
        UUID sessionId,
        ChatReferenceOrigin origin,
        UUID introducedByMessageId,
        ChatSourceType sourceType,
        UUID sourceId,
        boolean available,
        String title,
        String snippet,
        OffsetDateTime createdAt) {

    public ChatSourcePointer pointer() {
        return new ChatSourcePointer(sourceType, sourceId);
    }
}
