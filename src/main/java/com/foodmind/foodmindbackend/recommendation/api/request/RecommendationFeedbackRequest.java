package com.foodmind.foodmindbackend.recommendation.api.request;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackCommand;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEventType;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRejectionReason;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record RecommendationFeedbackRequest(
        UUID candidateId,
        RecommendationFeedbackEventType eventType,
        RecommendationRejectionReason reasonCode,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal rating,
        Boolean booleanValue,
        UUID resultingFoodRecordId,
        OffsetDateTime effectiveUntil) {

    public RecommendationFeedbackCommand toCommand(UUID sessionId) {
        return new RecommendationFeedbackCommand(
                sessionId,
                candidateId,
                eventType,
                reasonCode,
                rating,
                booleanValue,
                resultingFoodRecordId,
                effectiveUntil);
    }
}
