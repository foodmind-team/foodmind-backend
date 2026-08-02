package com.foodmind.foodmindbackend.cooking.domain;

/**
 * Agent-native cooking plan lifecycle statuses.
 */
public enum CookingPlanStatus {
    PROCESSING,
    READY,
    NEEDS_CONFIRMATION,
    INFEASIBLE,
    FAILED
}
