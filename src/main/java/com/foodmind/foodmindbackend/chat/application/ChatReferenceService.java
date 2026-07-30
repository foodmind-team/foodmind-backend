package com.foodmind.foodmindbackend.chat.application;

import com.foodmind.foodmindbackend.chat.application.port.ChatReferenceQuery;
import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import com.foodmind.foodmindbackend.chat.domain.ChatSourcePointer;
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
public class ChatReferenceService {

    private final ChatRepository chatRepository;
    private final ChatReferenceQuery referenceQuery;

    public ChatReferenceService(ChatRepository chatRepository, ChatReferenceQuery referenceQuery) {
        this.chatRepository = chatRepository;
        this.referenceQuery = referenceQuery;
    }

    @Transactional
    public ChatReference share(UUID userId, UUID sessionId, ChatSourcePointer source) {
        chatRepository.findActiveOwnedSession(userId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        referenceQuery.resolveAuthorised(userId, source)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return chatRepository.upsertUserReference(userId, sessionId, source);
    }
}
