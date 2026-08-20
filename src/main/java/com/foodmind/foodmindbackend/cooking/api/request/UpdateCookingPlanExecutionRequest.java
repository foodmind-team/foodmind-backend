package com.foodmind.foodmindbackend.cooking.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One optimistic-concurrency update to a persisted cooking execution step. */
public record UpdateCookingPlanExecutionRequest(
        @NotBlank @Size(max = 160) String stepId,
        @NotBlank @Pattern(regexp = "IN_PROGRESS|COMPLETED") String status,
        @Min(0) long expectedVersion) {
}
