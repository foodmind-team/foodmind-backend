package com.foodmind.foodmindbackend.recommendation.domain;

import java.time.Duration;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public enum RecommendationRejectionReason {
    TOO_EXPENSIVE(Duration.ofDays(7)),
    TOO_FAR(Duration.ofDays(7)),
    NOT_IN_MOOD(Duration.ofDays(1)),
    DIETARY_CONCERN(Duration.ofDays(14)),
    ALLERGEN_CONCERN(Duration.ofDays(30)),
    RECENTLY_EATEN(Duration.ofDays(14)),
    PLACE_CONCERN(Duration.ofDays(14)),
    DO_NOT_RECOMMEND(null),
    OTHER(null);

    private final Duration temporaryConstraintDuration;

    RecommendationRejectionReason(Duration temporaryConstraintDuration) {
        this.temporaryConstraintDuration = temporaryConstraintDuration;
    }

    public Duration temporaryConstraintDuration() {
        return temporaryConstraintDuration;
    }
}
