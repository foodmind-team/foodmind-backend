package com.foodmind.foodmindbackend.cooking.domain.agent;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingAgentWarningResult(
        int sequenceNo,
        String warningCode,
        String message) {
}
