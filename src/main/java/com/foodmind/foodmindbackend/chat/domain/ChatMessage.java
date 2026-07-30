package com.foodmind.foodmindbackend.chat.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatMessage(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        ChatRoute route,
        ChatResponseStatus responseStatus,
        UUID correlationId,
        String agentTraceId,
        OffsetDateTime createdAt,
        List<ChatMessageSource> sources) {
}
