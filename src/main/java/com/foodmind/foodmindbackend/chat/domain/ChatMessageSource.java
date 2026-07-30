package com.foodmind.foodmindbackend.chat.domain;

import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatMessageSource(
        UUID referenceId,
        ChatSourceType sourceType,
        UUID sourceId,
        int sequenceNo,
        String title,
        String snippet,
        Map<String, Object> groundingMetadata) {
}
