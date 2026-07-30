package com.foodmind.foodmindbackend.chat.application.port;

import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentGenerationResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public interface ChatAgentPort {

    ChatAgentGenerationResult generate(ChatAgentCommand command);
}
