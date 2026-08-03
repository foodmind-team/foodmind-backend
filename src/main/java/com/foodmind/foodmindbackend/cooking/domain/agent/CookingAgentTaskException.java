package com.foodmind.foodmindbackend.cooking.domain.agent;

/**
 * Transport/protocol failure of an agent task API call, carrying the mapped
 * {@link CookingAgentFailureCode} so callers can materialise the terminal state.
 */
public class CookingAgentTaskException extends RuntimeException {

    private final CookingAgentFailureCode failureCode;

    public CookingAgentTaskException(CookingAgentFailureCode failureCode) {
        super(failureCode == null ? CookingAgentFailureCode.NON_2XX.name() : failureCode.name());
        this.failureCode = failureCode == null ? CookingAgentFailureCode.NON_2XX : failureCode;
    }

    public CookingAgentFailureCode getFailureCode() {
        return failureCode;
    }
}
