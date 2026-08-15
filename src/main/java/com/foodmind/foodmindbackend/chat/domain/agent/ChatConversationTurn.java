package com.foodmind.foodmindbackend.chat.domain.agent;

/**
 * A bounded, source-free conversation turn supplied to the read-only Chat Agent.
 */
public record ChatConversationTurn(
        String role,
        String content) {
}
