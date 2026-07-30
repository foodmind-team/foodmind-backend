package com.foodmind.foodmindbackend.recommendation.domain.agent;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record AgentCandidateResult(
        UUID candidateId,
        int rank,
        RecommendationType recommendationType,
        BigDecimal modelScore,
        List<ReasonCode> reasonCodes,
        String explanation,
        Map<String, Object> featureSnapshot) {

    public AgentCandidateResult {
        reasonCodes = List.copyOf(reasonCodes);
        featureSnapshot = featureSnapshot == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(featureSnapshot));
    }
}
