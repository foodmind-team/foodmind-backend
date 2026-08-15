package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record AgentChatRequest(
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        UUID userMessageId,
        String traceId,
        OffsetDateTime expiresAt,
        String message,
        ChatRoute requestedRoute,
        String delegationToken,
        List<AgentChatReferenceRequest> sharedReferences,
        List<AgentChatTurnRequest> recentTurns) {

    public static AgentChatRequest from(ChatAgentCommand command) {
        return new AgentChatRequest(
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.userMessageId(),
                command.traceId(),
                command.expiresAt(),
                command.message(),
                command.requestedRoute(),
                command.delegationToken(),
                command.sharedReferences().stream().map(AgentChatReferenceRequest::from).toList(),
                command.recentTurns().stream().map(AgentChatTurnRequest::from).toList());
    }
}
