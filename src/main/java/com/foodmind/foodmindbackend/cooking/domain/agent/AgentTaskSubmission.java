package com.foodmind.foodmindbackend.cooking.domain.agent;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirror of the agent's task-submit (202) response body. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentTaskSubmission(
        String taskId,
        AgentTaskStatus status,
        String location,
        String requestId) {
}
