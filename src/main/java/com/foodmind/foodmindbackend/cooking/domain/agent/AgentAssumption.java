package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's Assumption (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentAssumption(
        String text,
        BigDecimal confidence,
        List<AgentEvidenceRef> evidence) {

    public AgentAssumption {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
