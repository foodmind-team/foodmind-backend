package com.foodmind.foodmindbackend.recommendation.domain;

import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record RecommendationFeedbackTarget(
        UUID sessionId,
        UUID userId,
        UUID candidateId,
        UUID placeMealId,
        UUID mealId,
        UUID placeId,
        String eligibilityStatus) {

    public boolean returnedCandidate() {
        return "RETURNED".equals(eligibilityStatus);
    }
}
