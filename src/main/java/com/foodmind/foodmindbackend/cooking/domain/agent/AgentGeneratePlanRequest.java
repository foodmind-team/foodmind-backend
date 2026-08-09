package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's GeneratePlanRequest (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentGeneratePlanRequest(
        String requestId,
        String userId,
        List<AgentRecipeInput> recipes,
        List<String> dietaryRestrictions,
        List<String> userAllergens,
        Integer timeLimitMinutes,
        LocalDate cookingDate,
        OffsetDateTime servingAt,
        String servingTime,
        List<AgentInventoryLotSnapshot> inventoryLots,
        List<AgentKitchenResourceSnapshot> kitchenResources,
        List<AgentApprovedDecision> approvedDecisions,
        String schemaVersion,
        String planRevision,
        String region,
        List<Map<String, Object>> preparsedCandidates) {

    public AgentGeneratePlanRequest {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
        dietaryRestrictions = dietaryRestrictions == null ? List.of() : List.copyOf(dietaryRestrictions);
        userAllergens = userAllergens == null ? List.of() : List.copyOf(userAllergens);
        inventoryLots = inventoryLots == null ? List.of() : List.copyOf(inventoryLots);
        kitchenResources = kitchenResources == null ? List.of() : List.copyOf(kitchenResources);
        approvedDecisions = approvedDecisions == null ? List.of() : List.copyOf(approvedDecisions);
        preparsedCandidates = preparsedCandidates == null ? List.of() : List.copyOf(preparsedCandidates);
        schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
    }
}
