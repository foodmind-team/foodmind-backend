package com.foodmind.foodmindbackend.cooking.domain;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanWarning(
        int sequenceNo,
        String warningCode,
        String message) {
}
