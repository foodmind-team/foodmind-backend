package com.foodmind.foodmindbackend.integration.agent.dto;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record AgentRecommendationResponse(
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        String traceId,
        String agentTraceId,
        String status,
        String modelVersion,
        String featureSchemaVersion,
        List<AgentRecommendationCandidateResponse> candidates) {
}
