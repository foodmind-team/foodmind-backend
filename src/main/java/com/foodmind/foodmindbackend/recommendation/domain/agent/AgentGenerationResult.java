package com.foodmind.foodmindbackend.recommendation.domain.agent;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record AgentGenerationResult(
        boolean successful,
        AgentFailureCode failureCode,
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        String traceId,
        String agentTraceId,
        String modelStatus,
        String modelVersion,
        String featureSchemaVersion,
        List<AgentCandidateResult> candidates) {

    public AgentGenerationResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static AgentGenerationResult success(
            String contractVersion,
            UUID requestId,
            UUID sessionId,
            String traceId,
            String agentTraceId,
            String modelStatus,
            String modelVersion,
            String featureSchemaVersion,
            List<AgentCandidateResult> candidates) {
        return new AgentGenerationResult(
                true,
                null,
                contractVersion,
                requestId,
                sessionId,
                traceId,
                agentTraceId,
                modelStatus,
                modelVersion,
                featureSchemaVersion,
                candidates);
    }

    public static AgentGenerationResult failure(
            AgentFailureCode failureCode,
            String contractVersion,
            UUID requestId,
            UUID sessionId,
            String traceId,
            String agentTraceId) {
        return new AgentGenerationResult(
                false,
                failureCode,
                contractVersion,
                requestId,
                sessionId,
                traceId,
                agentTraceId,
                failureCode.modelStatus(),
                null,
                null,
                List.of());
    }
}
