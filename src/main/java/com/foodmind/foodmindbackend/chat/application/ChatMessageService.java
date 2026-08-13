package com.foodmind.foodmindbackend.chat.application;

import com.foodmind.foodmindbackend.chat.application.port.ChatAgentPort;
import com.foodmind.foodmindbackend.chat.application.port.ChatReferenceQuery;
import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatAgentResultValidator;
import com.foodmind.foodmindbackend.chat.domain.ChatAgentResultValidator.ChatAgentValidationException;
import com.foodmind.foodmindbackend.chat.domain.ChatCursor;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.ChatPage;
import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentGenerationResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Service
public class ChatMessageService {

    public static final int MAX_MESSAGE_LENGTH = 12000;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String CHAT_AGENT_CONTRACT_VERSION = "chat-agent-v1";

    private final ChatRepository chatRepository;
    private final ChatReferenceQuery referenceQuery;
    private final ChatTransactionService transactionService;
    private final ChatAgentPort chatAgentPort;
    private final DelegationTokenIssuer delegationTokenIssuer;
    private final MeterRegistry meterRegistry;
    private final ChatAgentResultValidator validator = new ChatAgentResultValidator();

    public ChatMessageService(
            ChatRepository chatRepository,
            ChatReferenceQuery referenceQuery,
            ChatTransactionService transactionService,
            ChatAgentPort chatAgentPort,
            DelegationTokenIssuer delegationTokenIssuer,
            MeterRegistry meterRegistry) {
        this.chatRepository = chatRepository;
        this.referenceQuery = referenceQuery;
        this.transactionService = transactionService;
        this.chatAgentPort = chatAgentPort;
        this.delegationTokenIssuer = delegationTokenIssuer;
        this.meterRegistry = meterRegistry;
    }

    public ChatMessage post(
            UUID userId,
            UUID sessionId,
            String content,
            List<UUID> referenceIds,
            Boolean useSessionReferences,
            String route) {
        String safeContent = validateContent(content);
        ChatRoute requestedRoute = validateRequestedRoute(route);
        List<UUID> requestedReferenceIds = validateReferenceIds(referenceIds);
        chatRepository.findOwnedSession(userId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean inheritSessionReferences = useSessionReferences == null || useSessionReferences;
        List<UUID> referenceScope = inheritSessionReferences && requestedReferenceIds.isEmpty()
                ? chatRepository.findSessionReferences(userId, sessionId).stream().map(ChatReference::id).toList()
                : requestedReferenceIds;
        List<ChatReference> sharedReferences = referenceScope.isEmpty()
                ? List.of()
                : referenceQuery.resolveSessionReferences(userId, sessionId, referenceScope);
        validateExplicitReferences(requestedReferenceIds, sharedReferences);
        String traceId = traceId();
        UUID correlationId = correlationUuid(traceId);
        UUID userMessageId = transactionService.beginMessage(userId, sessionId, safeContent, correlationId);
        DelegationTokenIssuer.IssuedDelegationToken delegation = delegationTokenIssuer.issue(
                userId,
                traceId,
                List.of(DelegationTokenIssuer.SCOPE_CHAT_SEARCH, DelegationTokenIssuer.SCOPE_CHAT_REFERENCE_RESOLVE),
                sharedReferences.stream().map(ChatReference::id).toList());
        ChatAgentCommand command = new ChatAgentCommand(
                CHAT_AGENT_CONTRACT_VERSION,
                UUID.randomUUID(),
                sessionId,
                userMessageId,
                userId,
                traceId,
                OffsetDateTime.now().plusMinutes(2),
                delegation.token(),
                requestedRoute,
                safeContent,
                sharedReferences);
        ChatAgentGenerationResult agentResult = invokeAgentOutsideTransaction(command);
        try {
            ValidatedChatAgentResult validated = validator.validate(
                    command.requestId(),
                    sessionId,
                    userMessageId,
                    traceId,
                    agentResult);
            validated.sources().forEach(source -> referenceQuery.resolveAuthorised(
                    userId,
                    new com.foodmind.foodmindbackend.chat.domain.ChatSourcePointer(source.sourceType(), source.sourceId()))
                    .orElseThrow(() -> new ChatAgentValidationException("Agent cited an inaccessible source.")));
            meterRegistry.counter("foodmind.chat.grounding.rejected", "rejected", "false").increment();
            meterRegistry.counter("foodmind.chat.route", "route", validated.route().name()).increment();
            return transactionService.completeGroundedMessage(userId, sessionId, userMessageId, validated);
        } catch (ChatAgentValidationException exception) {
            meterRegistry.counter("foodmind.chat.grounding.rejected", "rejected", "true").increment();
            return transactionService.markFailed(userId, sessionId, userMessageId, traceId);
        }
    }

    private ChatRoute validateRequestedRoute(String route) {
        if (route == null || route.isBlank()) {
            return null;
        }
        String normalized = route.trim().toUpperCase(Locale.ROOT);
        try {
            ChatRoute requested = ChatRoute.valueOf(normalized);
            if (requested == ChatRoute.OUT_OF_SCOPE) {
                throw new IllegalArgumentException("OUT_OF_SCOPE is agent-owned");
            }
            return requested;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Chat supports search, summary, comparison, and navigation only.");
        }
    }

    private List<UUID> validateReferenceIds(List<UUID> referenceIds) {
        if (referenceIds == null || referenceIds.isEmpty()) {
            return List.of();
        }
        if (referenceIds.size() > 20) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "At most 20 chat references can be cited.");
        }
        return referenceIds.stream().distinct().toList();
    }

    private void validateExplicitReferences(List<UUID> requestedReferenceIds, List<ChatReference> sharedReferences) {
        if (requestedReferenceIds.isEmpty()) {
            return;
        }
        Set<UUID> resolvedReferenceIds = sharedReferences.stream()
                .filter(ChatReference::available)
                .map(ChatReference::id)
                .collect(Collectors.toSet());
        if (!resolvedReferenceIds.containsAll(requestedReferenceIds)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public ChatPage<ChatMessage> list(UUID userId, UUID sessionId, int size, ChatCursor after) {
        chatRepository.findOwnedSession(userId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return chatRepository.findOwnedMessages(userId, sessionId, safeSize, after);
    }

    private ChatAgentGenerationResult invokeAgentOutsideTransaction(ChatAgentCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        ChatAgentGenerationResult result = chatAgentPort.generate(command);
        String status = result.successful() ? "SUCCESS" : result.failureCode().name();
        sample.stop(Timer.builder("foodmind.chat.agent.latency")
                .tag("status", status)
                .register(meterRegistry));
        if (!result.successful()) {
            meterRegistry.counter("foodmind.chat.tool.failure", "code", status).increment();
        }
        return result;
    }

    private String validateContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Chat message must not be blank.");
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Chat message must be 12000 characters or fewer.");
        }
        return trimmed;
    }

    private String traceId() {
        String current = CorrelationIdFilter.currentCorrelationId();
        return current == null ? UUID.randomUUID().toString() : current;
    }

    private UUID correlationUuid(String traceId) {
        try {
            return UUID.fromString(traceId);
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }
}
