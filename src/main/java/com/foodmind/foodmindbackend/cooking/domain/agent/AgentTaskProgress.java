package com.foodmind.foodmindbackend.cooking.domain.agent;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Progress mirror of an agent task; {@code completed_steps} maps via snake_case JSON. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentTaskProgress(
        String node,
        int completedSteps,
        String message) {
}
