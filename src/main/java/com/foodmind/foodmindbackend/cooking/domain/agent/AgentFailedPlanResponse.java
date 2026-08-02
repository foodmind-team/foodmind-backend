package com.foodmind.foodmindbackend.cooking.domain.agent;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's FailedPlanResponse (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentFailedPlanResponse(
        String status,
        String errorCode,
        String correlationId,
        String message) implements AgentPlanResponse {
}
