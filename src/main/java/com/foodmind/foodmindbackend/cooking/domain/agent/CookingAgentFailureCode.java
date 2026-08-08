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
    AGENT_UNAVAILABLE,
    // Native-contract failure codes (Task 2.1, pure additions).
    INVALID_RECIPE_TEXT,
    SAFETY_CONSTRAINT_VIOLATION,
    INSUFFICIENT_INVENTORY,
    NO_COMPATIBLE_RESOURCE,
    TASK_GRAPH_CYCLE,
    SCHEDULE_INFEASIBLE,
    SCHEDULE_UNKNOWN,
    SCHEDULE_VERIFICATION_FAILED,
    AGENT_INTERNAL_ERROR,
    OVERLOADED,
    INVALID_INTERNAL_CREDENTIAL,
    // Async task API codes (Task 08, pure additions).
    AGENT_TASK_NOT_FOUND,
    TASK_CANCELLED,
    TASK_EXPIRED
}
