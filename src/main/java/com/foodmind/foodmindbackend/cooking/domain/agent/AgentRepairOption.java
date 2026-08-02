package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's RepairOption (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentRepairOption(
        String optionId,
        String optionType,
        String description,
        List<String> changes,
        List<String> effects,
        String revalidationStatus) {

    public AgentRepairOption {
        changes = changes == null ? List.of() : List.copyOf(changes);
        effects = effects == null ? List.of() : List.copyOf(effects);
        revalidationStatus = revalidationStatus == null ? "validated" : revalidationStatus;
    }
}
