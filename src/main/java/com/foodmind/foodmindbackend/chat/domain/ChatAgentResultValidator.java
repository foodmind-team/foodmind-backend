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
    private static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_SUGGESTION_LENGTH = 200;
    private static final Set<String> ALLOWED_DESTINATIONS = Set.of(
            "INVENTORY",
            "SHOPPING_LISTS",
            "SAVED_RECIPES",
            "COOKING_PLANS",
            "RECOMMENDATIONS",
            "EXPLORE");

    public ValidatedChatAgentResult validate(
            UUID requestId,
            UUID sessionId,
            UUID userMessageId,
            String traceId,
            ChatAgentGenerationResult result) {
        // Reject anything that breaks the agent contract before it can reach persistence or the API response.
        if (result == null || !result.successful()) {
            throw new ChatAgentValidationException("Agent did not return a successful result.");
        }
        if (!requestId.equals(result.requestId())
                || !sessionId.equals(result.sessionId())
                || !userMessageId.equals(result.userMessageId())
                || !traceId.equals(result.traceId())) {
            throw new ChatAgentValidationException("Agent result does not match the request.");
        }
        if (result.responseStatus() == null) {
            throw new ChatAgentValidationException("Agent response status is required.");
        }
        String answer = result.answer() == null ? "" : result.answer().trim();
        if (answer.isBlank() || answer.length() > MAX_ANSWER_LENGTH) {
            throw new ChatAgentValidationException("Agent answer is missing or oversized.");
        }
        if (result.sources().size() > MAX_SOURCES) {
            throw new ChatAgentValidationException("Agent source list is oversized.");
        }
        List<String> suggestedQuestions = validateSuggestions(
                result.suggestedQuestions(),
                "question",
                null);
        List<String> suggestedDestinations = validateSuggestions(
                result.suggestedDestinations(),
                "destination",
                ALLOWED_DESTINATIONS);
        if (result.responseStatus() == ChatResponseStatus.UNSUPPORTED) {
            if (!result.sources().isEmpty()) {
                throw new ChatAgentValidationException("Unsupported answers must not cite sources.");
            }
            return new ValidatedChatAgentResult(
                    result.contractVersion(),
                    result.agentTraceId(),
                    result.responseStatus(),
                    answer,
                    List.of(),
                    suggestedQuestions,
                    suggestedDestinations);
        }
        if (result.responseStatus() != ChatResponseStatus.SUCCEEDED
                && result.responseStatus() != ChatResponseStatus.FALLBACK_SUCCEEDED) {
            throw new ChatAgentValidationException("Supported answers must succeed.");
        }
        Set<String> seen = new HashSet<>();
        int expectedSequence = 1;
        for (ChatAgentSourceResult source : result.sources()) {
            // Sources must be complete, ordered, and unique so the UI can render deterministic grounding.
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
        return new ValidatedChatAgentResult(
                result.contractVersion(),
                result.agentTraceId(),
                result.responseStatus(),
                answer,
                result.sources(),
                suggestedQuestions,
                suggestedDestinations);
    }

    private List<String> validateSuggestions(
            List<String> values,
            String label,
            Set<String> allowedValues) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_SUGGESTIONS) {
            throw new ChatAgentValidationException("Agent " + label + " suggestions are oversized.");
        }
        List<String> normalised = values.stream()
                .map(value -> value == null ? "" : value.trim())
                .toList();
        if (normalised.stream().anyMatch(value -> value.isBlank() || value.length() > MAX_SUGGESTION_LENGTH)) {
            throw new ChatAgentValidationException("Agent " + label + " suggestion is invalid.");
        }
        if (normalised.stream().distinct().count() != normalised.size()) {
            throw new ChatAgentValidationException("Agent " + label + " suggestions contain duplicates.");
        }
        if (allowedValues != null && !allowedValues.containsAll(normalised)) {
            throw new ChatAgentValidationException("Agent returned an unsupported destination.");
        }
        return List.copyOf(normalised);
    }

    public static class ChatAgentValidationException extends RuntimeException {

        public ChatAgentValidationException(String message) {
            super(message);
        }
    }
}
