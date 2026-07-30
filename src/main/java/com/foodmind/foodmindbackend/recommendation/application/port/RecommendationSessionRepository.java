package com.foodmind.foodmindbackend.recommendation.application.port;

import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationSessionSummary;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentResult;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.SelectedCandidate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public interface RecommendationSessionRepository {

    UUID createSession(UUID userId, RecommendationRequestContext request, Map<String, Object> requestSnapshot, UUID correlationId);

    Map<UUID, UUID> insertEvaluations(UUID sessionId, List<EvaluatedCandidate> candidates, String featureSchemaVersion);

    void markProcessing(UUID sessionId);

    void completeAgent(UUID userId, UUID sessionId, ValidatedAgentResult result);

    void completeFallback(
            UUID userId,
            UUID sessionId,
            List<SelectedCandidate> selectedCandidates,
            AgentFailureCode failureCode,
            String agentContractVersion,
            String agentTraceId);

    Optional<RecommendationResult> findResult(UUID userId, UUID sessionId, String traceId);

    List<RecommendationSessionSummary> history(UUID userId, int page, int size);

    long historyCount(UUID userId);
}
