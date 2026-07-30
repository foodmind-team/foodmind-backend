package com.foodmind.foodmindbackend.recommendation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationSessionSummary(
        UUID sessionId,
        UUID groupId,
        String status,
        String fallbackStatus,
        String fallbackVersion,
        int returnedCandidateCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {
}
