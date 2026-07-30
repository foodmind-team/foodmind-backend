package com.foodmind.foodmindbackend.recommendation.application.port;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEvent;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEventType;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackTarget;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public interface RecommendationFeedbackRepository {

    Optional<RecommendationFeedbackTarget> findTarget(UUID userId, UUID sessionId, UUID candidateId);

    boolean sessionOwnedBy(UUID userId, UUID sessionId);

    Optional<RecommendationFeedbackEventType> existingTerminalDecision(UUID userId, UUID sessionId, UUID candidateId);

    boolean resultingFoodRecordMatches(UUID userId, UUID foodRecordId, UUID mealId, UUID placeId);

    RecommendationFeedbackEvent insertOrResolveRetry(
            RecommendationFeedbackEvent event,
            String canonicalPayloadHash);

    Optional<RecommendationFeedbackEvent> findById(UUID userId, UUID eventId);

    Optional<OffsetDateTime> latestTemporaryConstraint(UUID userId, UUID candidateId);
}
