package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's SafetyPolicyRecord (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentSafetyPolicy(
        String region,
        String version,
        LocalDate effectiveAt,
        List<AgentPolicySource> sources) {

    public AgentSafetyPolicy {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
