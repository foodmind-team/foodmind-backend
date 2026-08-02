package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's CompletionItem (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentCompletionItem(
        String completionItemId,
        String ingredientName,
        List<String> recipeIds,
        List<AgentLotAllocation> allocations) {

    public AgentCompletionItem {
        recipeIds = recipeIds == null ? List.of() : List.copyOf(recipeIds);
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }
}
