package com.foodmind.foodmindbackend.cooking.domain.agent;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** One dish completion summary entry of a READY plan (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentDishCompletion(
        String dishId,
        int completionMinute,
        int taskCount,
        boolean isShared) {
}
