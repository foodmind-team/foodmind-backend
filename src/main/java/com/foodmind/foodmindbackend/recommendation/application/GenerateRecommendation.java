package com.foodmind.foodmindbackend.recommendation.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyRecord;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationContextQuery;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationSessionRepository;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.FallbackSelector;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.SelectedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.filter.HardFilterPipeline;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

@Service
public class GenerateRecommendation {

    private static final String OPERATION = "RECOMMENDATION_GENERATE";

    private final RecommendationContextQuery contextQuery;
    private final RecommendationSessionRepository sessionRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final HardFilterPipeline filterPipeline = new HardFilterPipeline();
    private final FallbackSelector fallbackSelector = new FallbackSelector();

    public GenerateRecommendation(
            RecommendationContextQuery contextQuery,
            RecommendationSessionRepository sessionRepository,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.contextQuery = contextQuery;
        this.sessionRepository = sessionRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecommendationResult handle(UUID userId, RecommendationRequestContext request, String idempotencyKey) {
        validateGroup(userId, request);
        Map<String, Object> requestSnapshot = requestSnapshot(request);
        String canonicalRequest = toJson(requestSnapshot);
        String requestHash = idempotencyService.sha256Hex(canonicalRequest);
        IdempotencyRecord idempotency = idempotencyService.begin(userId, OPERATION, idempotencyKey, requestHash);
        String traceId = traceId();

        if ("COMPLETED".equals(idempotency.state()) && idempotency.resourceId() != null) {
            return sessionRepository.findResult(userId, idempotency.resourceId(), traceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        if (!"IN_PROGRESS".equals(idempotency.state())) {
            throw new ApiException(ErrorCode.CONFLICT, "The idempotency record is not available for retry.");
        }

        RecommendationContext context = contextQuery.load(userId, request);
        List<EvaluatedCandidate> evaluatedCandidates = context.candidates().stream()
                .map(candidate -> filterPipeline.evaluate(request, context.preferences(), candidate))
                .toList();
        List<SelectedCandidate> selectedCandidates = fallbackSelector.select(evaluatedCandidates, context.preferences());

        UUID sessionId = sessionRepository.createSession(userId, request, requestSnapshot, correlationUuid(traceId));
        sessionRepository.insertEvaluations(sessionId, evaluatedCandidates);
        sessionRepository.markProcessing(sessionId);
        sessionRepository.completeFallback(sessionId, selectedCandidates);

        RecommendationResult result = sessionRepository.findResult(userId, sessionId, traceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        idempotencyService.complete(idempotency.id(), sessionId, 201, toJson(result));
        return result;
    }

    private void validateGroup(UUID userId, RecommendationRequestContext request) {
        if (request.groupId() != null && !contextQuery.activeGroupMember(userId, request.groupId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Map<String, Object> requestSnapshot(RecommendationRequestContext request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractVersion", "recommendation-public-v1");
        snapshot.put("groupId", request.groupId());
        snapshot.put("mealType", request.mealType());
        snapshot.put("maxBudget", request.maxBudget());
        snapshot.put("currency", request.currency());
        snapshot.put("area", request.area());
        snapshot.put("latitude", request.latitude());
        snapshot.put("longitude", request.longitude());
        snapshot.put("maxDistanceKm", request.maxDistanceKm());
        snapshot.put("mood", request.mood());
        snapshot.put("requestedFor", request.requestedFor());
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("avoidAllergenCodes", request.avoidAllergenCodes());
        constraints.put("requiredDietaryTagCodes", request.requiredDietaryTagCodes());
        constraints.put("maxSpiceLevel", request.maxSpiceLevel());
        constraints.put("minimumCleanlinessEvidenceScore", request.minimumCleanlinessEvidenceScore());
        snapshot.put("constraints", constraints);
        return snapshot;
    }

    private String traceId() {
        String current = CorrelationIdFilter.currentCorrelationId();
        return current == null ? UUID.randomUUID().toString() : current;
    }

    private UUID correlationUuid(String traceId) {
        try {
            return UUID.fromString(traceId);
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise recommendation payload.", exception);
        }
    }
}
