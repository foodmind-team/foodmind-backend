package com.foodmind.foodmindbackend.recommendation.domain.agent;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record RecommendationAgentCandidate(
        UUID candidateId,
        UUID placeMealId,
        CandidateEvidence evidence,
        Map<String, Object> featureSnapshot) {

    public RecommendationAgentCandidate {
        featureSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(featureSnapshot));
    }
}
