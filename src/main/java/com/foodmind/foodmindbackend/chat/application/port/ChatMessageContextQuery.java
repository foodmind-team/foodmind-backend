package com.foodmind.foodmindbackend.chat.application.port;

import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatConversationTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageContextQuery {

    List<ChatConversationTurn> findRecentTurns(
            UUID userId,
            UUID sessionId,
            UUID beforeMessageId,
            int limit);

    Optional<ChatMessage> findOwnedMessage(UUID userId, UUID sessionId, UUID messageId);
}
