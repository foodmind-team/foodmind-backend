package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's ApprovedDecision (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentApprovedDecision(
        String optionId,
        String optionType,
        Map<String, Object> payload,
        String planRevision) {

    public AgentApprovedDecision {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
