package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import org.springframework.stereotype.Component;

/**
 * Validates a terminal agent response, mirroring the agent's own
 * {@code validate_terminal_response} (rendering/responses.py).
 *
 * @throws IllegalArgumentException when the response is not well-formed.
 */
@Component
public class CookingPlanResultValidator {

    public void validate(AgentPlanResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Agent returned no response.");
        }
        switch (response.status()) {
            case "READY" -> validateReady((AgentReadyPlanResponse) response);
            case "NEEDS_CONFIRMATION" -> validateConfirmation((AgentConfirmationPlanResponse) response);
            case "INFEASIBLE" -> validateInfeasible((AgentInfeasiblePlanResponse) response);
            case "FAILED" -> validateFailed((AgentFailedPlanResponse) response);
            default -> throw new IllegalArgumentException("Unknown response status: " + response.status());
        }
    }

    private void validateReady(AgentReadyPlanResponse response) {
        if (response.solverStatus() == null || response.solverStatus().isBlank()) {
            throw new IllegalArgumentException("READY response: solver_status is empty");
        }
        if (response.makespanMinutes() <= 0) {
            throw new IllegalArgumentException("READY response: makespan must be > 0, got " + response.makespanMinutes());
        }
    }

    private void validateConfirmation(AgentConfirmationPlanResponse response) {
        boolean hasContent = !response.assumptions().isEmpty()
                || !response.repairOptions().isEmpty()
                || !response.questions().isEmpty()
                || !response.confirmationQuestions().isEmpty();
        if (!hasContent) {
            throw new IllegalArgumentException(
                    "NEEDS_CONFIRMATION response: must have at least one assumption, repair_option, or question");
        }
    }

    private void validateInfeasible(AgentInfeasiblePlanResponse response) {
        if (response.reasons().isEmpty()) {
            throw new IllegalArgumentException("INFEASIBLE response: must have at least one reason");
        }
    }

    private void validateFailed(AgentFailedPlanResponse response) {
        if (response.errorCode() == null || response.errorCode().isBlank()) {
            throw new IllegalArgumentException("FAILED response: error_code is empty");
        }
    }
}
