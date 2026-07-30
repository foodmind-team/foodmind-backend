package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.ValidatedCookingAgentResult;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Service
public class CookingTransactionService {

    private final CookingPlanRepository planRepository;
    private final Clock clock;

    public CookingTransactionService(CookingPlanRepository planRepository, Clock clock) {
        this.planRepository = planRepository;
        this.clock = clock;
    }

    @Transactional
    public CookingAgentCommand createProcessingPlan(
            UUID userId,
            CookingPlanRequestContext request,
            Map<String, Object> requestSnapshot,
            Map<String, Object> preferenceSnapshot,
            List<RecipeCandidate> candidates,
            String traceId,
            UUID correlationId) {
        return planRepository.createProcessingPlan(
                userId,
                request,
                requestSnapshot,
                preferenceSnapshot,
                candidates,
                traceId,
                correlationId);
    }

    @Transactional
    public void completePlan(UUID userId, UUID planId, ValidatedCookingAgentResult result) {
        planRepository.completePlan(userId, planId, result);
    }

    @Transactional
    public void markFailed(
            UUID userId,
            UUID planId,
            CookingAgentFailureCode failureCode,
            String agentContractVersion,
            String agentTraceId) {
        planRepository.markFailed(userId, planId, failureCode, agentContractVersion, agentTraceId);
    }
}
