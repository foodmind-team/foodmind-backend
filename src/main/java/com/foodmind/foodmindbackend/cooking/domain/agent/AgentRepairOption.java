package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import java.util.Map;
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
        String revalidationStatus,
        Map<String, Object> payload) {

    public AgentRepairOption {
        changes = changes == null ? List.of() : List.copyOf(changes);
        effects = effects == null ? List.of() : List.copyOf(effects);
        revalidationStatus = revalidationStatus == null ? "validated" : revalidationStatus;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public AgentRepairOption(
            String optionId,
            String optionType,
            String description,
            List<String> changes,
            List<String> effects,
            String revalidationStatus) {
        this(optionId, optionType, description, changes, effects, revalidationStatus, Map.of());
    }
}
