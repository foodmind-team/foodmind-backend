package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationResponse(
        UUID sessionId,
        String traceId,
        String status,
        String modelStatus,
        String modelVersion,
        String fallbackStatus,
        String fallbackVersion,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        List<RecommendationCandidateResponse> items,
        List<RecommendationCandidateResponse> candidates) {

    public static RecommendationResponse from(RecommendationResult result) {
        List<RecommendationCandidateResponse> candidates = result.items().stream()
                .map(RecommendationCandidateResponse::from)
                .toList();
        return new RecommendationResponse(
                result.sessionId(),
                result.traceId(),
                result.status(),
                result.modelStatus(),
                result.modelVersion(),
                result.fallbackStatus(),
                result.fallbackVersion(),
                result.createdAt(),
                result.completedAt(),
                candidates,
                candidates);
    }
}
