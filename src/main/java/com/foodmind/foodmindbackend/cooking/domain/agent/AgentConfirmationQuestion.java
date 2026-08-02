package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's ConfirmationQuestion (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentConfirmationQuestion(
        String questionId,
        String fieldPath,
        String prompt,
        String responseType,
        List<AgentQuestionOption> options,
        boolean required,
        String suggestedValue) {

    public AgentConfirmationQuestion {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
