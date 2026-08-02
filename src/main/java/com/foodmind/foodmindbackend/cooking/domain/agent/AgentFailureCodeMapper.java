package com.foodmind.foodmindbackend.cooking.domain.agent;

/**
 * Maps the agent's {@code error_code} (domain/errors.py) onto the backend failure code.
 * Unknown or unmapped codes fail closed to {@link CookingAgentFailureCode#AGENT_INTERNAL_ERROR}.
 */
public final class AgentFailureCodeMapper {

    private AgentFailureCodeMapper() {
    }

    public static CookingAgentFailureCode map(String agentErrorCode) {
        if (agentErrorCode == null) {
            return CookingAgentFailureCode.AGENT_INTERNAL_ERROR;
        }
        return switch (agentErrorCode) {
            case "INVALID_RECIPE_TEXT" -> CookingAgentFailureCode.INVALID_RECIPE_TEXT;
            case "SAFETY_CONSTRAINT_VIOLATION" -> CookingAgentFailureCode.SAFETY_CONSTRAINT_VIOLATION;
            case "INSUFFICIENT_INVENTORY" -> CookingAgentFailureCode.INSUFFICIENT_INVENTORY;
            case "NO_COMPATIBLE_RESOURCE" -> CookingAgentFailureCode.NO_COMPATIBLE_RESOURCE;
            case "TASK_GRAPH_CYCLE" -> CookingAgentFailureCode.TASK_GRAPH_CYCLE;
            case "SCHEDULE_INFEASIBLE" -> CookingAgentFailureCode.SCHEDULE_INFEASIBLE;
            case "SCHEDULE_UNKNOWN" -> CookingAgentFailureCode.SCHEDULE_UNKNOWN;
            case "SCHEDULE_VERIFICATION_FAILED" -> CookingAgentFailureCode.SCHEDULE_VERIFICATION_FAILED;
            case "EXTERNAL_PROVIDER_UNAVAILABLE" -> CookingAgentFailureCode.AGENT_UNAVAILABLE;
            default -> CookingAgentFailureCode.AGENT_INTERNAL_ERROR;
        };
    }
}
