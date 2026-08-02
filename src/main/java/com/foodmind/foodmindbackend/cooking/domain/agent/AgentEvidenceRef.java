package com.foodmind.foodmindbackend.cooking.domain.agent;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's EvidenceRef (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentEvidenceRef(
        String sourceType,
        String title,
        String url,
        String retrievedAt) {
}
