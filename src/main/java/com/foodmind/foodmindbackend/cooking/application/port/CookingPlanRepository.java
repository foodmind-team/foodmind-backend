package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists agent-native cooking plans (PROCESSING root, then one of the four
 * terminal states with their materialised child tables) and reads them back.
 */
public interface CookingPlanRepository {

    UUID createProcessing(UUID userId, AgentGeneratePlanRequest request, List<AgentRecipeInput> sources,
                          String traceId, String rawRequestJson);

    void completeReady(UUID userId, UUID planId, AgentReadyPlanResponse response, String rawResponseJson);

    void completeConfirmation(UUID userId, UUID planId, AgentConfirmationPlanResponse response, String rawResponseJson);

    void completeInfeasible(UUID userId, UUID planId, AgentInfeasiblePlanResponse response, String rawResponseJson);

    void completeFailed(UUID userId, UUID planId, CookingAgentFailureCode code, AgentFailedPlanResponse response,
                        String rawResponseJson);

    Optional<CookingPlanResult> findOwned(UUID userId, UUID planId);

    List<CookingPlanSummary> findOwnedPage(UUID userId, int page, int size);

    long countOwned(UUID userId);
}
