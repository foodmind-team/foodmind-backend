package com.foodmind.foodmindbackend.recommendation.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record TrainingSnapshotOutputRow(
        String userKey,
        String mealKey,
        String offeringKey,
        OffsetDateTime decisionCreatedAt,
        int explicitLabel,
        BigDecimal laterRating,
        OffsetDateTime laterRatingCreatedAt,
        Boolean wouldEatAgain,
        OffsetDateTime wouldEatAgainCreatedAt,
        Object features,
        String featureSchemaVersion,
        Integer candidateRank,
        String candidateType,
        String modelVersion,
        String modelStatus,
        String fallbackVersion,
        String fallbackStatus) {
}
