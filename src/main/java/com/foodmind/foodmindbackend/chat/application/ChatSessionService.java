package com.foodmind.foodmindbackend.chat.application;

import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatPage;
import com.foodmind.foodmindbackend.chat.domain.ChatSession;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
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
public class ChatSessionService {

    private static final int MAX_TITLE_LENGTH = 160;

    private final ChatRepository chatRepository;

    public ChatSessionService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Transactional
    public ChatSession create(UUID userId, String title) {
        String safeTitle = title == null || title.isBlank() ? null : title.trim();
        if (safeTitle != null && safeTitle.length() > MAX_TITLE_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Session title must be 160 characters or fewer.");
        }
        return chatRepository.createSession(userId, safeTitle);
    }

    @Transactional(readOnly = true)
    public ChatPage<ChatSession> list(UUID userId, int page, int size) {
        return chatRepository.findOwnedSessions(userId, page, size);
    }

    @Transactional(readOnly = true)
    public ChatSession get(UUID userId, UUID sessionId) {
        return chatRepository.findOwnedSession(userId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public void archive(UUID userId, UUID sessionId) {
        chatRepository.archiveOwnedSession(userId, sessionId);
    }
}
