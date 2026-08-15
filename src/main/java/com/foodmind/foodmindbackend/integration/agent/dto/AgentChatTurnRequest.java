package com.foodmind.foodmindbackend.integration.agent.dto;

import com.foodmind.foodmindbackend.chat.domain.agent.ChatConversationTurn;

public record AgentChatTurnRequest(
        String role,
        String content) {

    public static AgentChatTurnRequest from(ChatConversationTurn turn) {
        return new AgentChatTurnRequest(turn.role(), turn.content());
    }
}
