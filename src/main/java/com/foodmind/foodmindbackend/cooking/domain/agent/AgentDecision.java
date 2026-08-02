package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's ApprovedDecision as emitted inside a confirmation response. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentDecision(
        String optionId,
        String optionType,
        Map<String, Object> payload,
        String planRevision) {

    public AgentDecision {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
