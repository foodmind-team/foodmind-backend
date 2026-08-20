package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanExecution;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Public saved state and cross-client execution progress for one owned plan. */
public record CookingPlanExecutionResponse(
        UUID planId,
        OffsetDateTime savedAt,
        OffsetDateTime finishedAt,
        long version,
        List<StepResponse> steps) {

    public static CookingPlanExecutionResponse from(CookingPlanExecution execution) {
        return new CookingPlanExecutionResponse(
                execution.planId(), execution.savedAt(), execution.finishedAt(), execution.version(),
                execution.steps().stream().map(StepResponse::from).toList());
    }

    public record StepResponse(String stepId, String status, OffsetDateTime updatedAt) {
        private static StepResponse from(CookingPlanExecution.Step step) {
            return new StepResponse(step.stepId(), step.status(), step.updatedAt());
        }
    }
}
