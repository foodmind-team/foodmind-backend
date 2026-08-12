package com.foodmind.foodmindbackend.chat.domain;

import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentGenerationResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentSourceResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public class ChatAgentResultValidator {

    private static final int MAX_ANSWER_LENGTH = 4000;
    private static final int MAX_SOURCES = 10;

    public ValidatedChatAgentResult validate(
            UUID requestId,
            UUID sessionId,
            UUID userMessageId,
            String traceId,
            ChatAgentGenerationResult result) {
        if (result == null || !result.successful()) {
            throw new ChatAgentValidationException("Agent did not return a successful result.");
        }
        if (!requestId.equals(result.requestId())
                || !sessionId.equals(result.sessionId())
                || !userMessageId.equals(result.userMessageId())
                || !traceId.equals(result.traceId())) {
            throw new ChatAgentValidationException("Agent result does not match the request.");
        }
        if (result.route() == null || result.responseStatus() == null) {
            throw new ChatAgentValidationException("Agent route and status are required.");
        }
        String answer = result.answer() == null ? "" : result.answer().trim();
        if (answer.isBlank() || answer.length() > MAX_ANSWER_LENGTH) {
            throw new ChatAgentValidationException("Agent answer is missing or oversized.");
        }
        if (result.sources().size() > MAX_SOURCES) {
            throw new ChatAgentValidationException("Agent source list is oversized.");
        }
        if (result.route() == ChatRoute.OUT_OF_SCOPE) {
            if (result.responseStatus() != ChatResponseStatus.UNSUPPORTED) {
                throw new ChatAgentValidationException("Out-of-scope answers must use unsupported status.");
            }
            return new ValidatedChatAgentResult(
                    result.contractVersion(),
                    result.agentTraceId(),
                    result.route(),
                    result.responseStatus(),
                    answer,
                    List.of());
        }
        if (result.responseStatus() != ChatResponseStatus.SUCCEEDED
                && result.responseStatus() != ChatResponseStatus.FALLBACK_SUCCEEDED) {
            throw new ChatAgentValidationException("Supported answers must succeed.");
        }
        Set<String> seen = new HashSet<>();
        int expectedSequence = 1;
        for (ChatAgentSourceResult source : result.sources()) {
            if (source.sourceType() == null || source.sourceId() == null) {
                throw new ChatAgentValidationException("Agent cited an incomplete source.");
            }
            if (source.sequenceNo() != expectedSequence++) {
                throw new ChatAgentValidationException("Agent source ordering is unstable.");
            }
            String key = source.sourceType().name() + ":" + source.sourceId();
            if (!seen.add(key)) {
                throw new ChatAgentValidationException("Agent cited a duplicate source.");
            }
        }
        if (result.sources().isEmpty() && result.route() != ChatRoute.NAVIGATION) {
            throw new ChatAgentValidationException("Grounded answers require at least one source.");
        }
        return new ValidatedChatAgentResult(
                result.contractVersion(),
                result.agentTraceId(),
                result.route(),
                result.responseStatus(),
                answer,
                result.sources());
    }

    public static class ChatAgentValidationException extends RuntimeException {

        public ChatAgentValidationException(String message) {
            super(message);
        }
    }
}
