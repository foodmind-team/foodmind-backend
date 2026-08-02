package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** One timeline entry of a READY plan (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentTimelineTask(
        String taskId,
        int startMinute,
        int endMinute,
        int durationMinutes,
        String instruction,
        String dishId,
        String workMode,
        String category,
        String heatLevel,
        List<String> resources,
        // Present only when the caller supplied an absolute serving instant
        // (the agent augments each entry with display context, P0-05).
        String servingAt,
        Integer offsetFromServingMinutes) {

    public AgentTimelineTask {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
