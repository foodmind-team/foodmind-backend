package com.foodmind.foodmindbackend.chat.domain.agent;

import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ValidatedChatAgentResult(
        String agentContractVersion,
        String agentTraceId,
        ChatRoute route,
        ChatResponseStatus responseStatus,
        String answer,
        List<ChatAgentSourceResult> sources) {
}
