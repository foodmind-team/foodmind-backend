package com.foodmind.foodmindbackend.integration.agent;

import com.foodmind.foodmindbackend.chat.application.port.ChatAgentPort;
import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentFailureCode;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentGenerationResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentSourceResult;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentChatRequest;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentChatResponse;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentChatSourceResponse;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Component
public class ChatAgentHttpAdapter implements ChatAgentPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatAgentHttpAdapter.class);

    private final RestClient restClient;
    private final ChatAgentClientProperties properties;
    private final ObjectMapper objectMapper;

    public ChatAgentHttpAdapter(
            @Qualifier("chatAgentRestClient") RestClient chatAgentRestClient,
            ChatAgentClientProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = chatAgentRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatAgentGenerationResult generate(ChatAgentCommand command) {
        if (!properties.isEnabled()) {
            return fallback(command);
        }
        if (properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            return fallback(command);
        }
        Instant startedAt = Instant.now();
        try {
            byte[] body = restClient.post()
                    .uri(properties.getEndpointPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceToken())
                    .header(CorrelationIdFilter.HEADER_NAME, command.traceId())
                    .header("X-FoodMind-Agent-Contract", command.contractVersion())
                    .body(AgentChatRequest.from(command))
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length > properties.getMaxResponseBytes()) {
                log(command, ChatAgentFailureCode.OVERSIZED_RESPONSE, startedAt);
                return failure(command, ChatAgentFailureCode.OVERSIZED_RESPONSE, null);
            }
            ChatAgentGenerationResult result = toResult(command, objectMapper.readValue(body, AgentChatResponse.class));
            log(command, result.failureCode(), startedAt);
            return result.successful() ? result : fallback(command);
        } catch (RestClientResponseException exception) {
            log(command, ChatAgentFailureCode.NON_2XX, startedAt);
            return fallback(command);
        } catch (ResourceAccessException exception) {
            ChatAgentFailureCode failureCode = timeout(exception)
                    ? ChatAgentFailureCode.TIMEOUT
                    : ChatAgentFailureCode.CONNECTION_ERROR;
            log(command, failureCode, startedAt);
            return fallback(command);
        } catch (JacksonException exception) {
            log(command, ChatAgentFailureCode.MALFORMED_JSON, startedAt);
            return fallback(command);
        } catch (IllegalArgumentException exception) {
            log(command, ChatAgentFailureCode.SCHEMA_MISMATCH, startedAt);
            return fallback(command);
        }
    }

    private ChatAgentGenerationResult toResult(ChatAgentCommand command, AgentChatResponse response) {
        if (response == null || !"SUCCEEDED".equals(response.status())) {
            return failure(command, ChatAgentFailureCode.SCHEMA_MISMATCH, response == null ? null : response.agentTraceId());
        }
        return ChatAgentGenerationResult.success(
                response.contractVersion(),
                response.requestId(),
                response.sessionId(),
                response.userMessageId(),
                response.traceId(),
                response.agentTraceId(),
                ChatRoute.valueOf(response.route()),
                ChatResponseStatus.valueOf(response.responseStatus()),
                response.answer(),
                sources(response.sources()));
    }

    private List<ChatAgentSourceResult> sources(List<AgentChatSourceResponse> sources) {
        if (sources == null) {
            return List.of();
        }
        return sources.stream()
                .map(source -> new ChatAgentSourceResult(
                        com.foodmind.foodmindbackend.chat.domain.ChatSourceType.valueOf(source.sourceType()),
                        source.sourceId(),
                        source.sequenceNo() == null ? 0 : source.sequenceNo(),
                        source.groundingMetadata() == null ? Map.of() : source.groundingMetadata()))
                .toList();
    }

    private ChatAgentGenerationResult fallback(ChatAgentCommand command) {
        String normalized = command.message().toLowerCase(Locale.ROOT);
        if (normalized.contains("recommend") || normalized.contains("cook") || normalized.contains("recipe")) {
            return ChatAgentGenerationResult.success(
                    command.contractVersion(),
                    command.requestId(),
                    command.sessionId(),
                    command.userMessageId(),
                    command.traceId(),
                    "chat-fallback",
                    ChatRoute.OUT_OF_SCOPE,
                    ChatResponseStatus.UNSUPPORTED,
                    "Chat supports searching, summarising, and comparing authorised FoodMind records, products, and places. Recommendations and cooking plans use their dedicated workflows.",
                    List.of());
        }
        List<ChatReference> available = command.sharedReferences().stream()
                .filter(ChatReference::available)
                .limit(3)
                .toList();
        if (available.isEmpty()) {
            return ChatAgentGenerationResult.success(
                    command.contractVersion(),
                    command.requestId(),
                    command.sessionId(),
                    command.userMessageId(),
                    command.traceId(),
                    "chat-fallback",
                    ChatRoute.OUT_OF_SCOPE,
                    ChatResponseStatus.UNSUPPORTED,
                    "I need authorised FoodMind records, products, or places before I can provide a grounded answer in Chat.",
                    List.of());
        }
        return ChatAgentGenerationResult.success(
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.userMessageId(),
                command.traceId(),
                "chat-fallback",
                ChatRoute.SUMMARY,
                ChatResponseStatus.FALLBACK_SUCCEEDED,
                "Here is a grounded summary of the shared FoodMind references: "
                        + available.stream().map(ChatReference::title).toList(),
                fallbackSources(available));
    }

    private List<ChatAgentSourceResult> fallbackSources(List<ChatReference> references) {
        int[] sequence = {1};
        return references.stream()
                .map(reference -> new ChatAgentSourceResult(
                        reference.sourceType(),
                        reference.sourceId(),
                        sequence[0]++,
                        Map.of("referenceId", reference.id().toString(), "origin", reference.origin().name())))
                .toList();
    }

    private ChatAgentGenerationResult failure(ChatAgentCommand command, ChatAgentFailureCode failureCode, String agentTraceId) {
        return ChatAgentGenerationResult.failure(
                failureCode,
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.userMessageId(),
                command.traceId(),
                agentTraceId);
    }

    private boolean timeout(ResourceAccessException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void log(ChatAgentCommand command, ChatAgentFailureCode failureCode, Instant startedAt) {
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        LOGGER.info(
                "chat_agent_call sessionId={} userMessageId={} traceId={} durationMs={} contractVersion={} failureCode={}",
                command.sessionId(),
                command.userMessageId(),
                command.traceId(),
                durationMs,
                command.contractVersion(),
                failureCode == null ? "NONE" : failureCode.name());
    }
}
