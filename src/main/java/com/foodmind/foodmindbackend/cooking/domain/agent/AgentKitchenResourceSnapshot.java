package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's KitchenResourceSnapshot (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentKitchenResourceSnapshot(
        String resourceId,
        String resourceType,
        BigDecimal capacity,
        String capacityUnit,
        List<String> capabilities,
        boolean available) {

    public AgentKitchenResourceSnapshot {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
