package com.foodmind.foodmindbackend.integration.agent.dto;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record AgentChatResponse(
        String status,
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        UUID userMessageId,
        String traceId,
        String agentTraceId,
        String responseStatus,
        String answer,
        List<AgentChatSourceResponse> sources,
        List<String> suggestedQuestions,
        List<String> suggestedDestinations) {
}
