package com.foodmind.foodmindbackend.chat.domain.agent;

import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatAgentCommand(
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        UUID userMessageId,
        UUID userId,
        String traceId,
        OffsetDateTime expiresAt,
        String delegationToken,
        String message,
        List<ChatReference> sharedReferences,
        List<ChatConversationTurn> recentTurns) {

    public ChatAgentCommand(
            String contractVersion,
            UUID requestId,
            UUID sessionId,
            UUID userMessageId,
            UUID userId,
            String traceId,
            OffsetDateTime expiresAt,
            String delegationToken,
            String message,
            List<ChatReference> sharedReferences) {
        this(
                contractVersion,
                requestId,
                sessionId,
                userMessageId,
                userId,
                traceId,
                expiresAt,
                delegationToken,
                message,
                sharedReferences,
                List.of());
    }
}
