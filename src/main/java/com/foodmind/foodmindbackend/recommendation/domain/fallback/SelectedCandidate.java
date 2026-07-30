package com.foodmind.foodmindbackend.recommendation.domain.fallback;

import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.math.BigDecimal;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record SelectedCandidate(
        EvaluatedCandidate candidate,
        RecommendationType type,
        int rank,
        BigDecimal fallbackScore,
        List<ReasonCode> reasonCodes,
        String explanation) {

    public SelectedCandidate {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
