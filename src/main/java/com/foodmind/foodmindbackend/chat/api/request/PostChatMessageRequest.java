package com.foodmind.foodmindbackend.chat.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record PostChatMessageRequest(
        @NotBlank
        @Size(max = 12000)
        String content,
        @Size(max = 20)
        List<UUID> referenceIds,
        Boolean useSessionReferences) {
}
