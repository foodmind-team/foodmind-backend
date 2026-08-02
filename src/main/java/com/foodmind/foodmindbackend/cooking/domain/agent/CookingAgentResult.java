package com.foodmind.foodmindbackend.cooking.domain.agent;

/**
 * Raw agent terminal state plus optional protocol/business failure — the port return type.
 * {@code successful()} is false for protocol failures (null response) and for business
 * {@code FAILED} responses alike.
 */
public record CookingAgentResult(
        AgentPlanResponse response,
        CookingAgentFailureCode failureCode,
        String rawResponseJson) {

    public boolean successful() {
        return response != null && !"FAILED".equals(response.status());
    }

    public static CookingAgentResult of(AgentPlanResponse response, String rawResponseJson) {
        return new CookingAgentResult(response, null, rawResponseJson);
    }

    public static CookingAgentResult failure(CookingAgentFailureCode code, String rawResponseJson) {
        return new CookingAgentResult(null, code, rawResponseJson);
    }

    public static CookingAgentResult failed(AgentFailedPlanResponse response, CookingAgentFailureCode code, String rawResponseJson) {
        return new CookingAgentResult(response, code, rawResponseJson);
    }
}
