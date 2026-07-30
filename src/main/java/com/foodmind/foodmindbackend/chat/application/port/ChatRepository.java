package com.foodmind.foodmindbackend.chat.application.port;

import com.foodmind.foodmindbackend.chat.domain.ChatCursor;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.ChatPage;
import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import com.foodmind.foodmindbackend.chat.domain.ChatSession;
import com.foodmind.foodmindbackend.chat.domain.ChatSourcePointer;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public interface ChatRepository {

    ChatSession createSession(UUID userId, String title);

    ChatPage<ChatSession> findOwnedSessions(UUID userId, int page, int size);

    Optional<ChatSession> findOwnedSession(UUID userId, UUID sessionId);

    Optional<ChatSession> findActiveOwnedSession(UUID userId, UUID sessionId);

    void archiveOwnedSession(UUID userId, UUID sessionId);

    UUID insertUserMessage(UUID userId, UUID sessionId, String content, UUID correlationId);

    ChatMessage insertAssistantMessage(UUID userId, UUID sessionId, UUID userMessageId, ValidatedChatAgentResult result);

    ChatMessage insertFailedAssistantMessage(UUID userId, UUID sessionId, UUID userMessageId, String traceId);

    ChatPage<ChatMessage> findOwnedMessages(UUID userId, UUID sessionId, int size, ChatCursor after);

    List<ChatReference> findSessionReferences(UUID userId, UUID sessionId);

    ChatReference upsertUserReference(UUID userId, UUID sessionId, ChatSourcePointer source);
}
