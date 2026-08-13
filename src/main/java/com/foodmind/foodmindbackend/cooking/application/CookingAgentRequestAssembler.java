package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.application.port.InventoryQuery;
import com.foodmind.foodmindbackend.cooking.application.port.KitchenResourceQuery;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentApprovedDecision;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.integration.agent.CookingAgentClientProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Assembles the agent-native {@link AgentGeneratePlanRequest} from the public request context. */
@Component
public class CookingAgentRequestAssembler {

    private final RecipeTextRenderer renderer;
    private final InventoryQuery inventoryQuery;
    private final KitchenResourceQuery kitchenResourceQuery;
    private final CookingAgentClientProperties properties;
    private final CookingAgentPort cookingAgentPort;

    public CookingAgentRequestAssembler(
            RecipeTextRenderer renderer,
            InventoryQuery inventoryQuery,
            KitchenResourceQuery kitchenResourceQuery,
            CookingAgentClientProperties properties,
            CookingAgentPort cookingAgentPort) {
        this.renderer = renderer;
        this.inventoryQuery = inventoryQuery;
        this.kitchenResourceQuery = kitchenResourceQuery;
        this.properties = properties;
        this.cookingAgentPort = cookingAgentPort;
    }

    public AgentGeneratePlanRequest assemble(
            UUID userId,
            CookingPlanRequestContext request,
            CookingPreferenceRules mergedRules,
            List<RecipeCandidate> candidates,
            String traceId) {
        return assemble(userId, request, mergedRules, candidates, traceId, true);
    }

    /**
     * Builds an async request without making a synchronous LLM preprocessing call.
     * The Cooking Agent worker performs the same parsing after the task has been
     * accepted, allowing clients to receive a task handle and progress immediately.
     */
    public AgentGeneratePlanRequest assembleForAsync(
            UUID userId,
            CookingPlanRequestContext request,
            CookingPreferenceRules mergedRules,
            List<RecipeCandidate> candidates,
            String traceId) {
        return assemble(userId, request, mergedRules, candidates, traceId, false);
    }

    private AgentGeneratePlanRequest assemble(
            UUID userId,
            CookingPlanRequestContext request,
            CookingPreferenceRules mergedRules,
            List<RecipeCandidate> candidates,
            String traceId,
            boolean preprocess) {
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
                properties.getRegion(),
                preprocess ? preprocessCandidates(candidates, request.servings()) : List.of());
    }

    /** Rebuilds a persisted request with the user's current inventory snapshot. */
    public AgentGeneratePlanRequest refreshInventory(
            UUID userId,
            AgentGeneratePlanRequest base,
            String requestId,
            List<AgentApprovedDecision> approvedDecisions,
            String planRevision) {
        return new AgentGeneratePlanRequest(
                sanitise(requestId),
                base.userId(),
                base.recipes(),
                base.dietaryRestrictions(),
                base.userAllergens(),
                base.timeLimitMinutes(),
                LocalDate.now(),
                base.servingAt(),
                base.servingTime(),
                inventoryQuery.lots(userId),
                kitchenResourceQuery.resources(userId),
                approvedDecisions,
                base.schemaVersion(),
                planRevision,
                base.region(),
                base.preparsedCandidates());
    }

    /**
     * Reuses the agent's NL parsing + gap-filling pipeline before generate():
     * raw recipe text goes in, fully-populated structured candidates come out.
     * The backend passes them back as {@code preparsed_candidates} so the
     * agent never re-parses and never asks gap/assumption questions. When the
     * preprocess call fails, we degrade gracefully — the agent's own parsing
     * pipeline still runs inside generate (no user-facing regression).
     */
    private List<Map<String, Object>> preprocessCandidates(List<RecipeCandidate> candidates, int servings) {
        try {
            return cookingAgentPort.preprocess(recipeInputs(candidates, servings));
        } catch (RuntimeException exception) {
            return List.of();
        }
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
