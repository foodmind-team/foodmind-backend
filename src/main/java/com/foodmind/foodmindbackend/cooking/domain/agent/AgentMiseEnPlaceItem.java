package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** One mise-en-place entry of a READY plan (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentMiseEnPlaceItem(
        String instruction,
        String ingredient,
        String operation,
        Integer durationMinutes,
        List<String> resources,
        String whenNeeded) {

    public AgentMiseEnPlaceItem {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
