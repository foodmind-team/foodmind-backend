package com.foodmind.foodmindbackend.recommendation.domain.agent;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public enum AgentFailureCode {
    AGENT_DISABLED("NOT_REQUESTED"),
    CONFIGURATION_ERROR("NOT_REQUESTED"),
    TIMEOUT("TIMED_OUT"),
    CONNECTION_ERROR("FAILED"),
    NON_2XX("FAILED"),
    MALFORMED_JSON("INVALID_RESPONSE"),
    OVERSIZED_RESPONSE("INVALID_RESPONSE"),
    SCHEMA_MISMATCH("INVALID_RESPONSE"),
    UNKNOWN_ID("INVALID_RESPONSE"),
    INVALID_REASON("INVALID_RESPONSE"),
    UNSUPPORTED_VERSION("INVALID_RESPONSE"),
    INFERENCE_UNAVAILABLE("UNAVAILABLE");

    private final String modelStatus;

    AgentFailureCode(String modelStatus) {
        this.modelStatus = modelStatus;
    }

    public String modelStatus() {
        return modelStatus;
    }
}
