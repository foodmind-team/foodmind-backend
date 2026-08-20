package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Public history card for a cooking plan (agent-native statuses). */
public record CookingPlanSummaryResponse(
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

    public static CookingPlanSummaryResponse from(CookingPlanSummary summary) {
        return new CookingPlanSummaryResponse(
                summary.planId(),
                summary.status(),
                summary.sourceCount(),
                summary.taskCount(),
                summary.completedStepCount(),
                summary.makespanMinutes(),
                summary.dishNames(),
                summary.createdAt(),
                summary.completedAt(),
                summary.savedAt(),
                summary.finishedAt());
    }
}
