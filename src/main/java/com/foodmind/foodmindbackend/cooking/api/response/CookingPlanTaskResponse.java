package com.foodmind.foodmindbackend.cooking.api.response;

import java.util.UUID;

/**
 * Async task status of a PROCESSING cooking plan. Terminal plans are not served
 * here (404): clients switch to {@code GET /cooking-plans/{planId}}.
 */
public record CookingPlanTaskResponse(
        UUID planId,
        String taskId,
        String status,
        String syncState,
        CookingPlanTaskProgressResponse progress) {
}
