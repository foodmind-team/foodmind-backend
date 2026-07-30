package com.foodmind.foodmindbackend.chat.application;

import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
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

    public ChatTransactionService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Transactional
    public UUID beginMessage(UUID userId, UUID sessionId, String content, UUID correlationId) {
        return chatRepository.insertUserMessage(userId, sessionId, content, correlationId);
    }

    @Transactional
    public ChatMessage completeGroundedMessage(
            UUID userId,
            UUID sessionId,
            UUID userMessageId,
            ValidatedChatAgentResult result) {
        return chatRepository.insertAssistantMessage(userId, sessionId, userMessageId, result);
    }

    @Transactional
    public ChatMessage markFailed(UUID userId, UUID sessionId, UUID userMessageId, String traceId) {
        return chatRepository.insertFailedAssistantMessage(userId, sessionId, userMessageId, traceId);
    }
}
