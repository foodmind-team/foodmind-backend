package com.foodmind.foodmindbackend.recommendation.domain.agent;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record ValidatedAgentCandidate(
        UUID candidateId,
        RecommendationType recommendationType,
        int rank,
        BigDecimal modelScore,
        List<ReasonCode> reasonCodes,
        String explanation) {

    public ValidatedAgentCandidate {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
