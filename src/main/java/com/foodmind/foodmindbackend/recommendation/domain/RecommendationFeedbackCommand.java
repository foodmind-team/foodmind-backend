package com.foodmind.foodmindbackend.recommendation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record RecommendationFeedbackCommand(
        UUID sessionId,
        UUID candidateId,
        RecommendationFeedbackEventType eventType,
        RecommendationRejectionReason reasonCode,
        BigDecimal rating,
        Boolean booleanValue,
        UUID resultingFoodRecordId,
        OffsetDateTime effectiveUntil) {
}
