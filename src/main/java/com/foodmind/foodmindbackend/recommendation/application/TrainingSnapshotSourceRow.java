package com.foodmind.foodmindbackend.recommendation.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record TrainingSnapshotSourceRow(
        UUID userId,
        UUID rawOfferingId,
        UUID rawMealId,
        int explicitLabel,
        OffsetDateTime decisionCreatedAt,
        BigDecimal laterRating,
        OffsetDateTime laterRatingCreatedAt,
        Boolean wouldEatAgain,
        OffsetDateTime wouldEatAgainCreatedAt,
        Integer candidateRank,
        String candidateType,
        String featureSchemaVersion,
        String rawFeatureSnapshot,
        String modelVersion,
        String modelStatus,
        String fallbackVersion,
        String fallbackStatus) {
}
