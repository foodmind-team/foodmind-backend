package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.application.SubmitFeedback.SubmitFeedbackResult;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEvent;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEventType;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRejectionReason;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record RecommendationFeedbackResponse(
        UUID feedbackId,
        UUID sessionId,
        UUID candidateId,
        RecommendationFeedbackEventType eventType,
        RecommendationRejectionReason reasonCode,
        BigDecimal rating,
        Boolean booleanValue,
        UUID resultingFoodRecordId,
        OffsetDateTime effectiveUntil,
        OffsetDateTime createdAt,
        Integer supervisedLabel) {

    public static RecommendationFeedbackResponse from(SubmitFeedbackResult result) {
        RecommendationFeedbackEvent event = result.event();
        return new RecommendationFeedbackResponse(
                event.id(),
                event.sessionId(),
                event.candidateId(),
                event.eventType(),
                event.reasonCode(),
                event.rating(),
                event.booleanValue(),
                event.resultingFoodRecordId(),
                event.effectiveUntil(),
                event.createdAt(),
                result.supervisedLabel());
    }
}
