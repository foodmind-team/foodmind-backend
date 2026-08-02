package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's ConfirmationPlanResponse (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentConfirmationPlanResponse(
        String planId,
        String status,
        List<AgentAssumption> assumptions,
        List<AgentRepairOption> repairOptions,
        List<String> questions,
        List<AgentConfirmationQuestion> confirmationQuestions,
        List<AgentDecision> decisions,
        String planRevision,
        AgentSafetyPolicy safetyPolicy) implements AgentPlanResponse {

    public AgentConfirmationPlanResponse {
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        repairOptions = repairOptions == null ? List.of() : List.copyOf(repairOptions);
        questions = questions == null ? List.of() : List.copyOf(questions);
        confirmationQuestions = confirmationQuestions == null ? List.of() : List.copyOf(confirmationQuestions);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
