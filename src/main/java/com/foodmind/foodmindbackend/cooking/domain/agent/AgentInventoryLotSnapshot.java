package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.math.BigDecimal;
import java.time.LocalDate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's InventoryLotSnapshot (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentInventoryLotSnapshot(
        String lotId,
        String itemId,
        String canonicalName,
        BigDecimal onHand,
        BigDecimal reserved,
        String unit,
        LocalDate expiryDate) {
}
