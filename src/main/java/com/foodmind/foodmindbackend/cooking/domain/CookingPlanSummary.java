package com.foodmind.foodmindbackend.cooking.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight history card for a cooking plan (agent-native statuses). */
public record CookingPlanSummary(
        UUID planId,
        String status,
        int sourceCount,
        int taskCount,
        Integer makespanMinutes,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {
}
