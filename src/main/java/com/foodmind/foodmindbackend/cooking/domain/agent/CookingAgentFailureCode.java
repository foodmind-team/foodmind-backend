package com.foodmind.foodmindbackend.cooking.domain.agent;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public enum CookingAgentFailureCode {
    AGENT_DISABLED,
    CONFIGURATION_ERROR,
    TIMEOUT,
    CONNECTION_ERROR,
    NON_2XX,
    MALFORMED_JSON,
    OVERSIZED_RESPONSE,
    SCHEMA_MISMATCH,
    UNKNOWN_RECIPE,
    INVALID_WARNING,
    UNSUPPORTED_VERSION,
    CONSTRAINT_CONFLICT,
    NO_RECIPE_MATCH,
    AGENT_UNAVAILABLE
}
