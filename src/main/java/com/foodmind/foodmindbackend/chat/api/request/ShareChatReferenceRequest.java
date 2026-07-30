package com.foodmind.foodmindbackend.chat.api.request;

import com.foodmind.foodmindbackend.chat.domain.ChatSourcePointer;
import com.foodmind.foodmindbackend.chat.domain.ChatSourceType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ShareChatReferenceRequest(
        @NotNull
        ChatSourceType sourceType,
        @NotNull
        UUID sourceId) {

    public ChatSourcePointer toSource() {
        return new ChatSourcePointer(sourceType, sourceId);
    }
}
