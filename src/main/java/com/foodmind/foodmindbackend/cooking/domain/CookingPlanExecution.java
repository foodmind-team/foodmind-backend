package com.foodmind.foodmindbackend.cooking.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Mutable owner-scoped execution state attached to an immutable READY plan. */
public record CookingPlanExecution(
        UUID planId,
        OffsetDateTime savedAt,
        OffsetDateTime finishedAt,
        long version,
        List<Step> steps) {

    public CookingPlanExecution {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public record Step(String stepId, String status, OffsetDateTime updatedAt) {
    }
}
