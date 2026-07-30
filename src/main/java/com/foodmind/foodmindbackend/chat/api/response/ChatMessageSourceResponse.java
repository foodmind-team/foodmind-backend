package com.foodmind.foodmindbackend.chat.api.response;

import com.foodmind.foodmindbackend.chat.domain.ChatMessageSource;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatMessageSourceResponse(
        UUID referenceId,
        String sourceType,
        UUID sourceId,
        int sequenceNo,
        String title,
        String snippet) {

    public static ChatMessageSourceResponse from(ChatMessageSource source) {
        return new ChatMessageSourceResponse(
                source.referenceId(),
                source.sourceType().name(),
                source.sourceId(),
                source.sequenceNo(),
                source.title(),
                source.snippet());
    }
}
