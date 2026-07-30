package com.foodmind.foodmindbackend.cooking.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanSummary(
        UUID planId,
        String status,
        UUID sourceRecipeId,
        int inputCount,
        int stepCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {
}
