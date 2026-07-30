package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentGenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Configuration
class CookingAgentFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(CookingAgentPort.class)
    CookingAgentPort disabledCookingAgentPort() {
        return command -> CookingAgentGenerationResult.failure(
                CookingAgentFailureCode.AGENT_DISABLED,
                null,
                command.requestId(),
                command.planId(),
                command.traceId(),
                null);
    }
}
