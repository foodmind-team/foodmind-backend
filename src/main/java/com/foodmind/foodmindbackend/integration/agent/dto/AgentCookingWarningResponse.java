package com.foodmind.foodmindbackend.integration.agent.dto;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record AgentCookingWarningResponse(
        Integer sequenceNo,
        String warningCode,
        String message) {
}
