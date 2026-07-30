package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanStep;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanStepResponse(
        int stepNo,
        String instruction) {

    public static CookingPlanStepResponse from(CookingPlanStep step) {
        return new CookingPlanStepResponse(step.stepNo(), step.instruction());
    }
}
