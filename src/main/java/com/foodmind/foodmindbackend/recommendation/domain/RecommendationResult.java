package com.foodmind.foodmindbackend.recommendation.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationResult(
        UUID sessionId,
        String traceId,
        String status,
        String modelStatus,
        String modelVersion,
        String fallbackStatus,
        String fallbackVersion,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        RecommendationDecisionProfile decisionProfile,
        List<RecommendationCandidateResult> items) {

    public RecommendationResult {
        items = List.copyOf(items);
    }
}
