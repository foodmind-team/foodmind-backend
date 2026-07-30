package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanWarning;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingPlanWarningResponse(
        int sequenceNo,
        String warningCode,
        String message) {

    public static CookingPlanWarningResponse from(CookingPlanWarning warning) {
        return new CookingPlanWarningResponse(warning.sequenceNo(), warning.warningCode(), warning.message());
    }
}
