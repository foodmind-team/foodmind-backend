package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Public history card for a cooking plan (agent-native statuses). */
public record CookingPlanSummaryResponse(
        UUID planId,
        String status,
        int sourceCount,
        int taskCount,
        Integer makespanMinutes,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {

    public static CookingPlanSummaryResponse from(CookingPlanSummary summary) {
        return new CookingPlanSummaryResponse(
                summary.planId(),
                summary.status(),
                summary.sourceCount(),
                summary.taskCount(),
                summary.makespanMinutes(),
                summary.createdAt(),
                summary.completedAt());
    }
}
