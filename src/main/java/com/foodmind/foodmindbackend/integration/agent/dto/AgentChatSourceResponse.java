package com.foodmind.foodmindbackend.integration.agent.dto;

import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record AgentChatSourceResponse(
        String sourceType,
        UUID sourceId,
        Integer sequenceNo,
        Map<String, Object> groundingMetadata) {
}
