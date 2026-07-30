package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentGenerationResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public interface CookingAgentPort {

    CookingAgentGenerationResult generate(CookingAgentCommand command);
}
