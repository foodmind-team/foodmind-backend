package com.foodmind.foodmindbackend.chat.domain.agent;

import com.foodmind.foodmindbackend.chat.domain.ChatSourceType;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatAgentSourceResult(
        ChatSourceType sourceType,
        UUID sourceId,
        int sequenceNo,
        Map<String, Object> groundingMetadata) {
}
