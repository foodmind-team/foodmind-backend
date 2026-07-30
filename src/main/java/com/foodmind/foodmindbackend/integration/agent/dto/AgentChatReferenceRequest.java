package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record AgentChatReferenceRequest(UUID referenceId, String sourceType, UUID sourceId, String title, String snippet) {

    public static AgentChatReferenceRequest from(ChatReference reference) {
        return new AgentChatReferenceRequest(
                reference.id(),
                reference.sourceType().name(),
                reference.sourceId(),
                reference.title(),
                reference.snippet());
    }
}
