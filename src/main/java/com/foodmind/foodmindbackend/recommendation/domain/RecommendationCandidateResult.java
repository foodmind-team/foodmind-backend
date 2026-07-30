package com.foodmind.foodmindbackend.recommendation.domain;

import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationCandidateResult(
        UUID candidateId,
        UUID placeMealId,
        UUID mealId,
        String mealName,
        UUID placeId,
        String placeName,
        String area,
        MoneyAmount price,
        RecommendationType recommendationType,
        int rank,
        List<ReasonCode> reasonCodes,
        String explanation,
        BigDecimal fallbackScore) {

    public RecommendationCandidateResult {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
