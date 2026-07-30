package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record AgentRecommendationRequest(
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        String traceId,
        OffsetDateTime deadlineAt,
        Map<String, Object> requestContext,
        Map<String, Object> preferenceContext,
        List<AgentRecommendationCandidateRequest> candidates) {

    public static AgentRecommendationRequest from(RecommendationAgentCommand command) {
        return new AgentRecommendationRequest(
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.traceId(),
                command.deadlineAt(),
                command.requestContext(),
                command.preferenceContext(),
                command.candidates().stream()
                        .map(AgentRecommendationCandidateRequest::from)
                        .toList());
    }
}
