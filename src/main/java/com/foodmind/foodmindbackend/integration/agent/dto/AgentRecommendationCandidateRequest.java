package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCandidate;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record AgentRecommendationCandidateRequest(
        UUID candidateId,
        String candidateKey,
        Map<String, Object> features) {

    public static AgentRecommendationCandidateRequest from(RecommendationAgentCandidate candidate) {
        return new AgentRecommendationCandidateRequest(
                candidate.candidateId(),
                candidate.candidateKey(),
                candidate.featureSnapshot());
    }
}
