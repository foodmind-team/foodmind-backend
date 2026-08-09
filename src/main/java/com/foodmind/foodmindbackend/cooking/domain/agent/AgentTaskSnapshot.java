package com.foodmind.foodmindbackend.cooking.domain.agent;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Mirror of the agent's task summary (get/cancel response). The {@code result} and
 * {@code error} objects are re-serialised by the adapter into JSON strings so the
 * materialisation layer can reuse the existing {@link AgentPlanResponse} hierarchy.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentTaskSnapshot(
        String taskId,
        AgentTaskStatus status,
        String requestId,
        String location,
        AgentTaskProgress progress,
        String resultJson,
        String errorJson) {
}
