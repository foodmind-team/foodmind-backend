package com.foodmind.foodmindbackend.cooking.api.response;

import java.util.UUID;

/** 202 response body of {@code POST /cooking-plans/generate-async}. */
public record CookingPlanAsyncAcceptedResponse(
        UUID planId,
        String status,
        String taskId,
        String location) {
}
