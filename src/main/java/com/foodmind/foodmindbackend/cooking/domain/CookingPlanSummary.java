package com.foodmind.foodmindbackend.cooking.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Lightweight history card for a cooking plan (agent-native statuses). */
public record CookingPlanSummary(
        UUID planId,
        String status,
        int sourceCount,
        int taskCount,
        int completedStepCount,
        Integer makespanMinutes,
        List<String> dishNames,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime savedAt,
        OffsetDateTime finishedAt) {

    public CookingPlanSummary {
        dishNames = dishNames == null ? List.of() : List.copyOf(dishNames);
    }
}
