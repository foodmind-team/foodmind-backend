package com.foodmind.foodmindbackend.chat.api.response;

import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatMessageResponse(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        String route,
        String responseStatus,
        UUID correlationId,
        String agentTraceId,
        OffsetDateTime createdAt,
        List<ChatMessageSourceResponse> sources,
        List<String> suggestedQuestions,
        List<String> suggestedDestinations) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.id(),
                message.sessionId(),
                message.role(),
                message.content(),
                message.route() == null ? null : message.route().name(),
                message.responseStatus() == null ? null : message.responseStatus().name(),
                message.correlationId(),
                message.agentTraceId(),
                message.createdAt(),
                message.sources().stream().map(ChatMessageSourceResponse::from).toList(),
                message.suggestedQuestions(),
                message.suggestedDestinations());
    }
}
