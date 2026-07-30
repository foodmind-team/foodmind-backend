package com.foodmind.foodmindbackend.recommendation.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationContext(
        PreferenceEvidence preferences,
        List<CandidateEvidence> candidates) {

    public RecommendationContext {
        candidates = List.copyOf(candidates);
    }
}
