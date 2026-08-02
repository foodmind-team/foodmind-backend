package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's InfeasiblePlanResponse (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentInfeasiblePlanResponse(
        String planId,
        String status,
        List<String> reasons,
        List<String> safeAlternatives) implements AgentPlanResponse {

    public AgentInfeasiblePlanResponse {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        safeAlternatives = safeAlternatives == null ? List.of() : List.copyOf(safeAlternatives);
    }
}
