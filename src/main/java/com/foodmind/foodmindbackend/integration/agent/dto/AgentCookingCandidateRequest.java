package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCandidate;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record AgentCookingCandidateRequest(
        UUID recipeId,
        Map<String, Object> snapshot) {

    public static AgentCookingCandidateRequest from(CookingAgentCandidate candidate) {
        return new AgentCookingCandidateRequest(candidate.recipeId(), candidate.snapshot());
    }
}
