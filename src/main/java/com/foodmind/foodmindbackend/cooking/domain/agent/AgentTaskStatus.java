package com.foodmind.foodmindbackend.cooking.domain.agent;

/** Mirrors the agent's task state machine (TaskStatus) for the async task API. */
public enum AgentTaskStatus {
    QUEUED,
    RUNNING,
    READY,
    NEEDS_CONFIRMATION,
    INFEASIBLE,
    FAILED,
    CANCELLED,
    EXPIRED
}
