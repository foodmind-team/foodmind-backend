package com.foodmind.foodmindbackend.recommendation.application.port;

import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public interface RecommendationAgentPort {

    AgentGenerationResult generate(RecommendationAgentCommand command);
}
