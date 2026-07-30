package com.foodmind.foodmindbackend.integration.agent.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record AgentRecommendationCandidateResponse(
        UUID candidateId,
        Integer rank,
        String recommendationType,
        BigDecimal modelScore,
        List<String> reasonCodes,
        String explanation,
        Map<String, Object> featureSnapshot) {
}
