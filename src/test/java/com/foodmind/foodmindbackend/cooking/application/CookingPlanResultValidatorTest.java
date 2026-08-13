package com.foodmind.foodmindbackend.cooking.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class CookingPlanResultValidatorTest {

    private final CookingPlanResultValidator validator = new CookingPlanResultValidator();

    @Test
    void rejectsReadyWithoutMakespan() {
        AgentReadyPlanResponse ready = new AgentReadyPlanResponse(
                "p-1", "READY", "OPTIMAL", 0, List.of(), List.of(), List.of(), List.of(), null, null, null, List.of());

        assertThatThrownBy(() -> validator.validate(ready))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("makespan");
    }

    @Test
    void rejectsReadyWithoutSolverStatus() {
        AgentReadyPlanResponse ready = new AgentReadyPlanResponse(
                "p-1", "READY", "", 54, List.of(), List.of(), List.of(), List.of(), null, null, null, List.of());

        assertThatThrownBy(() -> validator.validate(ready))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solver_status");
    }

    @Test
    void acceptsReadyWithValidContent() {
        AgentReadyPlanResponse ready = new AgentReadyPlanResponse(
                "p-1", "READY", "OPTIMAL", 54, List.of(), List.of(), List.of(), List.of(), null, null, null, List.of());

        assertThatCode(() -> validator.validate(ready)).doesNotThrowAnyException();
    }

    @Test
    void rejectsConfirmationWithoutContent() {
        AgentConfirmationPlanResponse confirmation = new AgentConfirmationPlanResponse(
                "p-1", "NEEDS_CONFIRMATION", List.of(), List.of(), List.of(), List.of(), List.of(), "p-1:v1", null);

        assertThatThrownBy(() -> validator.validate(confirmation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionable");
    }

    @Test
    void rejectsConfirmationWithOnlyLegacyQuestion() {
        AgentConfirmationPlanResponse confirmation = new AgentConfirmationPlanResponse(
                "p-1", "NEEDS_CONFIRMATION", List.of(), List.of(), List.of("Would you like to proceed?"),
                List.of(), List.of(), "p-1:v1", null);

        assertThatThrownBy(() -> validator.validate(confirmation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionable");
    }

    @Test
    void acceptsConfirmationWithAnActionableStructuredQuestion() {
        AgentConfirmationQuestion question = new AgentConfirmationQuestion(
                "gap:temperature", "steps[0].target_temperature_c", "What target temperature?",
                "TEXT", List.of(), true, null);
        AgentConfirmationPlanResponse confirmation = new AgentConfirmationPlanResponse(
                "p-1", "NEEDS_CONFIRMATION", List.of(), List.of(), List.of(question.prompt()),
                List.of(question), List.of(), "p-1:v1", null);

        assertThatCode(() -> validator.validate(confirmation)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInfeasibleWithoutReasons() {
        AgentInfeasiblePlanResponse infeasible =
                new AgentInfeasiblePlanResponse("p-1", "INFEASIBLE", List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(infeasible))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void rejectsFailedWithoutErrorCode() {
        AgentFailedPlanResponse failed = new AgentFailedPlanResponse("FAILED", "", "c-1", "message");

        assertThatThrownBy(() -> validator.validate(failed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error_code");
    }
}
