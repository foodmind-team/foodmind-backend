package com.foodmind.foodmindbackend.chat.application;

import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Service
public class ChatTransactionService {

    private final ChatRepository chatRepository;
    private final IdempotencyService idempotencyService;

    public ChatTransactionService(ChatRepository chatRepository, IdempotencyService idempotencyService) {
        this.chatRepository = chatRepository;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public UUID beginMessage(UUID userId, UUID sessionId, String content, UUID correlationId) {
        return beginMessage(userId, sessionId, content, correlationId, null);
    }

    @Transactional
    public UUID beginMessage(
            UUID userId,
            UUID sessionId,
            String content,
            UUID correlationId,
            UUID idempotencyRecordId) {
        // Store the user message first so the agent result can be attached to a durable conversation record.
        UUID userMessageId = chatRepository.insertUserMessage(userId, sessionId, content, correlationId);
        if (idempotencyRecordId != null) {
            idempotencyService.associateResource(idempotencyRecordId, userMessageId);
        }
        return userMessageId;
    }

    @Transactional
    public ChatMessage completeGroundedMessage(
            UUID userId,
            UUID sessionId,
            UUID userMessageId,
            ValidatedChatAgentResult result) {
        return completeGroundedMessage(userId, sessionId, userMessageId, result, null);
    }

    @Transactional
    public ChatMessage completeGroundedMessage(
            UUID userId,
            UUID sessionId,
            UUID userMessageId,
            ValidatedChatAgentResult result,
            UUID idempotencyRecordId) {
        // Persist the assistant reply only after validation has proven the response is safe to expose.
        ChatMessage stored = chatRepository.insertAssistantMessage(userId, sessionId, userMessageId, result);
        completeIdempotency(idempotencyRecordId, stored.id());
        return stored.withSuggestions(result.suggestedQuestions(), result.suggestedDestinations());
    }

    @Transactional
    public ChatMessage markFailed(UUID userId, UUID sessionId, UUID userMessageId, String traceId) {
        return markFailed(userId, sessionId, userMessageId, traceId, null);
    }

    @Transactional
    public ChatMessage markFailed(
            UUID userId,
            UUID sessionId,
            UUID userMessageId,
            String traceId,
            UUID idempotencyRecordId) {
        ChatMessage stored = chatRepository.insertFailedAssistantMessage(userId, sessionId, userMessageId, traceId);
        completeIdempotency(idempotencyRecordId, stored.id());
        return stored;
    }

    private void completeIdempotency(UUID idempotencyRecordId, UUID assistantMessageId) {
        if (idempotencyRecordId != null) {
            idempotencyService.complete(idempotencyRecordId, assistantMessageId, 201, "{}");
        }
    }
}
