package com.foodmind.foodmindbackend.cooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentAssumption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentCompletionItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDecision;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDishCompletion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentLotAllocation;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentMiseEnPlaceItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPolicySource;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentQuestionOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRepairOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentSafetyPolicy;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTimelineTask;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CookingPlanResultMapperTest {

    private final CookingPlanResultMapper mapper = new CookingPlanResultMapper();

    @Test
    void mapsReadyResponseWithAllSubLists() {
        AgentReadyPlanResponse ready = new AgentReadyPlanResponse(
                "p-1", "READY", "OPTIMAL", 54,
                List.of(new AgentTimelineTask("t-1", 0, 6, 6, "Pan-fry the tofu.", "d-1", "ACTIVE", "preparation",
                        "MEDIUM", List.of("stove"), null, null)),
                List.of(new AgentCompletionItem("c-1", "chilli", List.of("d-1"),
                        List.of(new AgentLotAllocation("lot-9", new BigDecimal("30.000"), "g")))),
                List.of(new AgentMiseEnPlaceItem("dice: chicken breast", "chicken breast", "dice", 6,
                        List.of("knife"), "diced_chicken")),
                List.of(new AgentDishCompletion("d-1", 54, 9, false)),
                new AgentSafetyPolicy("SG", "sg-v1", LocalDate.of(2026, 1, 1),
                        List.of(new AgentPolicySource("s-1", "title", "url"))),
                "explanation", "deterministic");

        CookingPlanResult result = mapper.toResult(ready);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.solverStatus()).isEqualTo("OPTIMAL");
        assertThat(result.makespanMinutes()).isEqualTo(54);
        assertThat(result.timeline()).hasSize(1);
        assertThat(result.timeline().get(0).instruction()).isEqualTo("Pan-fry the tofu.");
        assertThat(result.timeline().get(0).heatLevel()).isEqualTo("MEDIUM");
        assertThat(result.completionChecklist().get(0).allocations().get(0).quantity()).isEqualByComparingTo("30.000");
        assertThat(result.miseEnPlace().get(0).operation()).isEqualTo("dice");
        assertThat(result.dishCompletions().get(0).completionMinute()).isEqualTo(54);
        assertThat(result.safetyPolicy().region()).isEqualTo("SG");
        assertThat(result.safetyPolicy().sources().get(0).url()).isEqualTo("url");
        assertThat(result.explanation()).isEqualTo("explanation");
        assertThat(result.explanationSource()).isEqualTo("deterministic");
    }

    @Test
    void mapsConfirmationResponse() {
        AgentConfirmationPlanResponse confirmation = new AgentConfirmationPlanResponse(
                "p-1", "NEEDS_CONFIRMATION",
                List.of(new AgentAssumption("assuming 200 C", new BigDecimal("0.82"), List.of())),
                List.of(new AgentRepairOption("opt-1", "reduce_servings", "Reduce to 2 servings",
                        List.of("servings 4 -> 2"), List.of("feasible"), "validated")),
                List.of("legacy question"),
                List.of(new AgentConfirmationQuestion("q-1", "recipe.r-1.assumptions", "Accept?",
                        "CHOICE", List.of(new AgentQuestionOption("accept", "Accept", true)), true, "200 C")),
                List.of(new AgentDecision("opt-1", "reduce_servings", Map.of("servings", 2), "p-1:v1")),
                "p-1:v1", null);

        CookingPlanResult result = mapper.toResult(confirmation);

        assertThat(result.status()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(result.planRevision()).isEqualTo("p-1:v1");
        assertThat(result.assumptions()).hasSize(1);
        assertThat(result.repairOptions().get(0).optionType()).isEqualTo("reduce_servings");
        assertThat(result.questions()).containsExactly("legacy question");
        assertThat(result.confirmationQuestions().get(0).questionId()).isEqualTo("q-1");
        assertThat(result.decisions().get(0).payload()).containsEntry("servings", 2);
    }

    @Test
    void mapsInfeasibleResponse() {
        AgentInfeasiblePlanResponse infeasible = new AgentInfeasiblePlanResponse(
                "p-1", "INFEASIBLE",
                List.of("Insufficient 'chilli': need 60 g, have 30 g"), List.of("Use dried chilli"));

        CookingPlanResult result = mapper.toResult(infeasible);

        assertThat(result.status()).isEqualTo("INFEASIBLE");
        assertThat(result.reasons()).containsExactly("Insufficient 'chilli': need 60 g, have 30 g");
        assertThat(result.safeAlternatives()).containsExactly("Use dried chilli");
    }

    @Test
    void mapsFailedResponse() {
        AgentFailedPlanResponse failed =
                new AgentFailedPlanResponse("FAILED", "SCHEDULE_UNKNOWN", "c-1", "timeout");

        CookingPlanResult result = mapper.toResult(failed);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("SCHEDULE_UNKNOWN");
        assertThat(result.errorMessage()).isEqualTo("timeout");
    }
}
