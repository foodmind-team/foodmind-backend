package com.foodmind.foodmindbackend.cooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.RecipeIngredientSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.RecipeStepSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInventoryLotSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentKitchenResourceSnapshot;
import com.foodmind.foodmindbackend.integration.agent.CookingAgentClientProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CookingAgentRequestAssemblerTest {

    private final RecipeTextRenderer renderer = new RecipeTextRenderer();

    @Test
    void assemblesNativeRequestFromContextAndCandidates() {
        CookingAgentClientProperties props = new CookingAgentClientProperties();
        props.setRegion("SG");
        CookingAgentRequestAssembler assembler = new CookingAgentRequestAssembler(
                renderer, userId -> List.of(), userId -> List.of(), props);

        UUID recipeId = UUID.randomUUID();
        CookingPlanRequestContext request = new CookingPlanRequestContext(
                List.of(), List.of(recipeId), 2, 60, null, null,
                List.of("VEGAN"), List.of("PEANUT"),
                OffsetDateTime.parse("2026-08-02T18:30:00+08:00"), "sg");
        CookingPreferenceRules merged = new CookingPreferenceRules(List.of("VEGAN"), List.of("PEANUT"));
        RecipeCandidate candidate = new RecipeCandidate(
                recipeId, "Tofu Bowl", "A simple bowl", 2, 35, null, null,
                List.of(), List.of(),
                List.of(new RecipeIngredientSnapshot(1, "Firm tofu", new BigDecimal("300"), "g", false)),
                List.of(new RecipeStepSnapshot(1, "Pan-fry the tofu.")));

        AgentGeneratePlanRequest agentRequest =
                assembler.assemble(UUID.randomUUID(), request, merged, List.of(candidate), "trace-1");

        assertThat(agentRequest.requestId()).isEqualTo("trace-1");
        assertThat(agentRequest.recipes()).hasSize(1);
        assertThat(agentRequest.recipes().get(0).recipeId()).isEqualTo(recipeId.toString());
        assertThat(agentRequest.recipes().get(0).text())
                .contains("Tofu Bowl")
                .contains("Firm tofu: 300 g")
                .contains("1. Pan-fry the tofu.");
        assertThat(agentRequest.recipes().get(0).targetServings()).isEqualByComparingTo("2");
        assertThat(agentRequest.dietaryRestrictions()).containsExactly("VEGAN");
        assertThat(agentRequest.userAllergens()).containsExactly("PEANUT");
        assertThat(agentRequest.timeLimitMinutes()).isEqualTo(60);
        assertThat(agentRequest.servingAt()).isEqualTo(OffsetDateTime.parse("2026-08-02T18:30:00+08:00"));
        assertThat(agentRequest.region()).isEqualTo("SG");
        assertThat(agentRequest.schemaVersion()).isEqualTo("1.0");
        assertThat(agentRequest.approvedDecisions()).isEmpty();
    }

    @Test
    void includesInventoryAndKitchenSnapshots() {
        AgentInventoryLotSnapshot lot = new AgentInventoryLotSnapshot(
                "lot-1", "item-1", "chicken breast", new BigDecimal("500"), new BigDecimal("100"), "g", null);
        AgentKitchenResourceSnapshot resource = new AgentKitchenResourceSnapshot(
                "res-1", "stove", new BigDecimal("4"), "burners", List.of("induction"), true);
        CookingAgentClientProperties props = new CookingAgentClientProperties();
        props.setRegion("SG");
        CookingAgentRequestAssembler assembler = new CookingAgentRequestAssembler(
                renderer, userId -> List.of(lot), userId -> List.of(resource), props);

        UUID recipeId = UUID.randomUUID();
        CookingPlanRequestContext request = new CookingPlanRequestContext(
                List.of(), List.of(recipeId), 2, null, null, null, List.of(), List.of(), null, "SG");
        RecipeCandidate candidate = new RecipeCandidate(
                recipeId, "Tofu Bowl", "A simple bowl", 2, 35, null, null,
                List.of(), List.of(), List.of(), List.of());

        AgentGeneratePlanRequest agentRequest = assembler.assemble(
                UUID.randomUUID(), request,
                new CookingPreferenceRules(List.of(), List.of()), List.of(candidate), "trace-2");

        assertThat(agentRequest.inventoryLots()).containsExactly(lot);
        assertThat(agentRequest.kitchenResources()).containsExactly(resource);
    }

    @Test
    void sanitisesTraceIdForAgentRequestId() {
        CookingAgentClientProperties props = new CookingAgentClientProperties();
        props.setRegion("SG");
        CookingAgentRequestAssembler assembler = new CookingAgentRequestAssembler(
                renderer, userId -> List.of(), userId -> List.of(), props);

        UUID recipeId = UUID.randomUUID();
        CookingPlanRequestContext request = new CookingPlanRequestContext(
                List.of(), List.of(recipeId), 2, null, null, null, List.of(), List.of(), null, "SG");
        RecipeCandidate candidate = new RecipeCandidate(
                recipeId, "Tofu Bowl", "A simple bowl", 2, 35, null, null,
                List.of(), List.of(), List.of(), List.of());

        AgentGeneratePlanRequest agentRequest = assembler.assemble(
                UUID.randomUUID(), request,
                new CookingPreferenceRules(List.of(), List.of()), List.of(candidate), "bad trace:with:colons");

        assertThat(agentRequest.requestId()).isEqualTo("bad-trace-with-colons");
    }
}
