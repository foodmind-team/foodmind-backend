package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Service
public class GetCookingPlan {

    private final CookingPlanRepository planRepository;

    public GetCookingPlan(CookingPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public CookingPlanResult handle(UUID userId, UUID planId) {
        return planRepository.findOwned(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public PageResponse<CookingPlanSummary> history(UUID userId, int page, int size) {
        return PageResponse.of(planRepository.findOwnedPage(userId, page, size), page, size, planRepository.countOwned(userId));
    }

    public PageResponse<CookingPlanSummary> saved(UUID userId, int page, int size) {
        return PageResponse.of(planRepository.findSavedPage(userId, page, size), page, size, planRepository.countSaved(userId));
    }
}
