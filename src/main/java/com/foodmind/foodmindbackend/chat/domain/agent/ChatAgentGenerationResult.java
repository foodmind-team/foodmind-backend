package com.foodmind.foodmindbackend.chat.domain.agent;

import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatAgentGenerationResult(
        boolean successful,
        ChatAgentFailureCode failureCode,
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        UUID userMessageId,
        String traceId,
        String agentTraceId,
        ChatResponseStatus responseStatus,
        String answer,
        List<ChatAgentSourceResult> sources,
        List<String> suggestedQuestions,
        List<String> suggestedDestinations) {

    public static ChatAgentGenerationResult success(
            String contractVersion,
            UUID requestId,
            UUID sessionId,
            UUID userMessageId,
            String traceId,
            String agentTraceId,
            ChatResponseStatus responseStatus,
            String answer,
            List<ChatAgentSourceResult> sources) {
        return success(
                contractVersion,
                requestId,
                sessionId,
                userMessageId,
                traceId,
                agentTraceId,
                responseStatus,
                answer,
                sources,
                List.of(),
                List.of());
    }

    public static ChatAgentGenerationResult success(
            String contractVersion,
            UUID requestId,
            UUID sessionId,
            UUID userMessageId,
            String traceId,
            String agentTraceId,
            ChatResponseStatus responseStatus,
            String answer,
            List<ChatAgentSourceResult> sources,
            List<String> suggestedQuestions,
            List<String> suggestedDestinations) {
        return new ChatAgentGenerationResult(
                true,
                null,
                contractVersion,
                requestId,
                sessionId,
                userMessageId,
                traceId,
                agentTraceId,
                responseStatus,
                answer,
                sources == null ? List.of() : List.copyOf(sources),
                suggestedQuestions == null ? List.of() : List.copyOf(suggestedQuestions),
                suggestedDestinations == null ? List.of() : List.copyOf(suggestedDestinations));
    }

    public static ChatAgentGenerationResult failure(
            ChatAgentFailureCode failureCode,
            String contractVersion,
            UUID requestId,
            UUID sessionId,
            UUID userMessageId,
            String traceId,
            String agentTraceId) {
        return new ChatAgentGenerationResult(
                false,
                failureCode,
                contractVersion,
                requestId,
                sessionId,
                userMessageId,
                traceId,
                agentTraceId,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
    }
}
