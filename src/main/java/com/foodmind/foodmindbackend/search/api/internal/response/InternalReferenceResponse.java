package com.foodmind.foodmindbackend.search.api.internal.response;

import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record InternalReferenceResponse(
        UUID referenceId,
        String sourceType,
        UUID sourceId,
        boolean available,
        String title,
        String snippet) {

    public static InternalReferenceResponse from(ChatReference reference) {
        return new InternalReferenceResponse(
                reference.id(),
                reference.sourceType().name(),
                reference.sourceId(),
                reference.available(),
                reference.title(),
                reference.snippet());
    }
}
