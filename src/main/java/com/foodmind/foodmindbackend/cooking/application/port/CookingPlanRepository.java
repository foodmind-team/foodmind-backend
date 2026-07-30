package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.ValidatedCookingAgentResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public interface CookingPlanRepository {

    CookingAgentCommand createProcessingPlan(
            UUID userId,
            CookingPlanRequestContext request,
            Map<String, Object> requestSnapshot,
            Map<String, Object> preferenceSnapshot,
            List<RecipeCandidate> candidates,
            String traceId,
            UUID correlationId);

    void completePlan(UUID userId, UUID planId, ValidatedCookingAgentResult result);

    void markFailed(
            UUID userId,
            UUID planId,
            CookingAgentFailureCode failureCode,
            String agentContractVersion,
            String agentTraceId);

    Optional<CookingPlanResult> findOwned(UUID userId, UUID planId, String traceId);

    List<CookingPlanSummary> findOwnedPage(UUID userId, int page, int size);

    long countOwned(UUID userId);
}
