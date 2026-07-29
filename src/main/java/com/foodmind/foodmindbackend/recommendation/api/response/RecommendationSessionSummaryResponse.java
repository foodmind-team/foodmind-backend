package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationSessionSummary;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationSessionSummaryResponse(
        UUID sessionId,
        UUID groupId,
        String status,
        String fallbackStatus,
        String fallbackVersion,
        int returnedCandidateCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {

    public static RecommendationSessionSummaryResponse from(RecommendationSessionSummary summary) {
        return new RecommendationSessionSummaryResponse(
                summary.sessionId(),
                summary.groupId(),
                summary.status(),
                summary.fallbackStatus(),
                summary.fallbackVersion(),
                summary.returnedCandidateCount(),
                summary.createdAt(),
                summary.completedAt());
    }
}
