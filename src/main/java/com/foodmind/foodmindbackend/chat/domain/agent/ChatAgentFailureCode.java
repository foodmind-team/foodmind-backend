package com.foodmind.foodmindbackend.chat.domain.agent;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public enum ChatAgentFailureCode {
    AGENT_DISABLED,
    CONFIGURATION_ERROR,
    NON_2XX,
    TIMEOUT,
    CONNECTION_ERROR,
    OVERSIZED_RESPONSE,
    MALFORMED_JSON,
    SCHEMA_MISMATCH,
    VALIDATION_REJECTED
}
