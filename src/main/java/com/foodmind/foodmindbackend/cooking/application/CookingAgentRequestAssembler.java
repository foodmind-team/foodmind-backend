package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.application.port.InventoryQuery;
import com.foodmind.foodmindbackend.cooking.application.port.KitchenResourceQuery;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.integration.agent.CookingAgentClientProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Assembles the agent-native {@link AgentGeneratePlanRequest} from the public request context. */
@Component
public class CookingAgentRequestAssembler {

    private final RecipeTextRenderer renderer;
    private final InventoryQuery inventoryQuery;
    private final KitchenResourceQuery kitchenResourceQuery;
    private final CookingAgentClientProperties properties;

    public CookingAgentRequestAssembler(
            RecipeTextRenderer renderer,
            InventoryQuery inventoryQuery,
            KitchenResourceQuery kitchenResourceQuery,
            CookingAgentClientProperties properties) {
        this.renderer = renderer;
        this.inventoryQuery = inventoryQuery;
        this.kitchenResourceQuery = kitchenResourceQuery;
        this.properties = properties;
    }

    public AgentGeneratePlanRequest assemble(
            UUID userId,
            CookingPlanRequestContext request,
            CookingPreferenceRules mergedRules,
            List<RecipeCandidate> candidates,
            String traceId) {
        return new AgentGeneratePlanRequest(
                sanitise(traceId),
                userId.toString(),
                recipeInputs(candidates, request.servings()),
                mergedRules.requiredDietaryTagCodes(),
                mergedRules.avoidAllergenCodes(),
                request.maxMinutes(),
                LocalDate.now(),
                request.servingAt(),
                null,
                inventoryQuery.lots(userId),
                kitchenResourceQuery.resources(userId),
                List.of(),
                "1.0",
                null,
                properties.getRegion());
    }

    /** Renders each candidate as {@code recipes[].text}; also used as the persisted source snapshot. */
    public List<AgentRecipeInput> recipeInputs(List<RecipeCandidate> candidates, int servings) {
        return candidates.stream()
                .map(candidate -> new AgentRecipeInput(
                        candidate.recipeId().toString(),
                        renderer.render(candidate),
                        BigDecimal.valueOf(servings)))
                .toList();
    }

    /** Agent request_id must be ≤128 chars of [A-Za-z0-9_-] (used as agent_request_id + correlation). */
    private String sanitise(String traceId) {
        if (traceId == null) {
            return UUID.randomUUID().toString();
        }
        String cleaned = traceId.replaceAll("[^A-Za-z0-9_-]", "-");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned;
    }
}
