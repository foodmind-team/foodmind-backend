package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;

/**
 * Port for invoking the cooking-plan agent over its native internal contract
 * ({@code POST /internal/v1/agents/cooking-plan/generate}, X-Internal-Token).
 */
public interface CookingAgentPort {

    CookingAgentResult generate(AgentGeneratePlanRequest request);
}
