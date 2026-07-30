package com.foodmind.foodmindbackend.chat.api.response;

import com.foodmind.foodmindbackend.chat.domain.ChatSession;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatSessionResponse(
        UUID id,
        String title,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.id(),
                session.title(),
                session.status(),
                session.createdAt(),
                session.updatedAt());
    }
}
