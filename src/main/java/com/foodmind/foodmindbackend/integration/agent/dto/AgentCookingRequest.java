package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record AgentCookingRequest(
        String contractVersion,
        UUID requestId,
        UUID planId,
        String traceId,
        OffsetDateTime deadlineAt,
        Map<String, Object> request,
        Map<String, Object> preferences,
        List<AgentCookingCandidateRequest> candidates) {

    public static AgentCookingRequest from(CookingAgentCommand command) {
        return new AgentCookingRequest(
                command.contractVersion(),
                command.requestId(),
                command.planId(),
                command.traceId(),
                command.deadlineAt(),
                command.requestSnapshot(),
                command.preferenceSnapshot(),
                command.candidates().stream().map(AgentCookingCandidateRequest::from).toList());
    }
}
