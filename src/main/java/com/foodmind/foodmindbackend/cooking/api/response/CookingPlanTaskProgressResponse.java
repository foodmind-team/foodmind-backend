package com.foodmind.foodmindbackend.cooking.api.response;

import java.util.UUID;

/** Progress of an in-flight async cooking-plan task ({@code GET /cooking-plans/{planId}/task}). */
public record CookingPlanTaskProgressResponse(
        String node,
        int completedSteps,
        String message) {
}
