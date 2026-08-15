package com.foodmind.foodmindbackend.chat.application;

import com.foodmind.foodmindbackend.chat.application.port.ChatAgentPort;
import com.foodmind.foodmindbackend.chat.application.port.ChatMessageContextQuery;
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
import com.foodmind.foodmindbackend.chat.domain.agent.ChatConversationTurn;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyAttempt;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final int MAX_RECENT_TURNS = 8;
    private static final String CHAT_AGENT_CONTRACT_VERSION = "chat-agent-v1";
    private static final String IDEMPOTENCY_OPERATION = "CHAT_MESSAGE_POST";

    private final ChatRepository chatRepository;
    private final ChatReferenceQuery referenceQuery;
    private final ChatMessageContextQuery messageContextQuery;
    private final ChatTransactionService transactionService;
    private final ChatAgentPort chatAgentPort;
    private final DelegationTokenIssuer delegationTokenIssuer;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;
    private final ChatAgentResultValidator validator = new ChatAgentResultValidator();

    @Autowired
    public ChatMessageService(
            ChatRepository chatRepository,
            ChatReferenceQuery referenceQuery,
            ChatMessageContextQuery messageContextQuery,
            ChatTransactionService transactionService,
            ChatAgentPort chatAgentPort,
            DelegationTokenIssuer delegationTokenIssuer,
            IdempotencyService idempotencyService,
            MeterRegistry meterRegistry) {
        this.chatRepository = chatRepository;
        this.referenceQuery = referenceQuery;
        this.messageContextQuery = messageContextQuery;
        this.transactionService = transactionService;
        this.chatAgentPort = chatAgentPort;
        this.delegationTokenIssuer = delegationTokenIssuer;
        this.idempotencyService = idempotencyService;
        this.meterRegistry = meterRegistry;
    }

    ChatMessageService(
            ChatRepository chatRepository,
            ChatReferenceQuery referenceQuery,
            ChatTransactionService transactionService,
            ChatAgentPort chatAgentPort,
            DelegationTokenIssuer delegationTokenIssuer,
            MeterRegistry meterRegistry) {
        this(
                chatRepository,
                referenceQuery,
                emptyContextQuery(),
                transactionService,
                chatAgentPort,
                delegationTokenIssuer,
                null,
                meterRegistry);
    }

    public ChatMessage post(
            UUID userId,
            UUID sessionId,
            String content,
            List<UUID> referenceIds,
            Boolean useSessionReferences,
            String route) {
        return post(userId, sessionId, content, referenceIds, useSessionReferences, route, null);
    }

    public ChatMessage post(
            UUID userId,
            UUID sessionId,
            String content,
            List<UUID> referenceIds,
            Boolean useSessionReferences,
            String route,
            String idempotencyKey) {
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

        IdempotencyAttempt idempotency = beginIdempotency(
                userId,
                sessionId,
                safeContent,
                requestedReferenceIds,
                inheritSessionReferences,
                requestedRoute,
                idempotencyKey);
        if (idempotency != null && "COMPLETED".equals(idempotency.record().state())) {
            return replayCompleted(userId, sessionId, idempotency);
        }
        if (idempotency != null && !idempotency.acquired()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "This message is still being processed. Retry with the same key shortly.");
        }

        UUID userMessageId = recoverUserMessage(userId, sessionId, safeContent, idempotency);
        List<ChatConversationTurn> recentTurns = messageContextQuery.findRecentTurns(
                userId,
                sessionId,
                userMessageId,
                MAX_RECENT_TURNS);
        String traceId = traceId();
        if (userMessageId == null) {
            userMessageId = idempotency == null
                    ? transactionService.beginMessage(
                            userId,
                            sessionId,
                            safeContent,
                            correlationUuid(traceId))
                    : transactionService.beginMessage(
                            userId,
                            sessionId,
                            safeContent,
                            correlationUuid(traceId),
                            idempotency.record().id());
        }

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
                sharedReferences,
                recentTurns);
        ChatAgentGenerationResult agentResult = invokeAgentOutsideTransaction(command);
        UUID idempotencyRecordId = idempotency == null ? null : idempotency.record().id();
        ChatMessage assistantMessage;
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
            assistantMessage = idempotencyRecordId == null
                    ? transactionService.completeGroundedMessage(
                            userId,
                            sessionId,
                            userMessageId,
                            validated)
                    : transactionService.completeGroundedMessage(
                            userId,
                            sessionId,
                            userMessageId,
                            validated,
                            idempotencyRecordId);
        } catch (ChatAgentValidationException exception) {
            meterRegistry.counter("foodmind.chat.grounding.rejected", "rejected", "true").increment();
            assistantMessage = idempotencyRecordId == null
                    ? transactionService.markFailed(userId, sessionId, userMessageId, traceId)
                    : transactionService.markFailed(
                            userId,
                            sessionId,
                            userMessageId,
                            traceId,
                            idempotencyRecordId);
        }
        return assistantMessage;
    }

    private IdempotencyAttempt beginIdempotency(
            UUID userId,
            UUID sessionId,
            String content,
            List<UUID> referenceIds,
            boolean useSessionReferences,
            ChatRoute route,
            String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String canonicalRequest = sessionId
                + "\ncontent=" + content.length() + ":" + content
                + "\nreferenceIds=" + referenceIds.stream().map(UUID::toString).collect(Collectors.joining(","))
                + "\nuseSessionReferences=" + useSessionReferences
                + "\nroute=" + (route == null ? "" : route.name());
        return idempotencyService.beginAttempt(
                userId,
                IDEMPOTENCY_OPERATION,
                idempotencyKey,
                idempotencyService.sha256Hex(canonicalRequest));
    }

    private ChatMessage replayCompleted(UUID userId, UUID sessionId, IdempotencyAttempt idempotency) {
        UUID assistantMessageId = idempotency.record().resourceId();
        if (assistantMessageId == null) {
            throw new ApiException(ErrorCode.CONFLICT, "The completed chat retry has no stored response.");
        }
        return messageContextQuery.findOwnedMessage(userId, sessionId, assistantMessageId)
                .filter(message -> "ASSISTANT".equals(message.role()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private UUID recoverUserMessage(
            UUID userId,
            UUID sessionId,
            String content,
            IdempotencyAttempt idempotency) {
        if (idempotency == null || idempotency.record().resourceId() == null) {
            return null;
        }
        ChatMessage stored = messageContextQuery.findOwnedMessage(
                userId,
                sessionId,
                idempotency.record().resourceId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if ("ASSISTANT".equals(stored.role())) {
            idempotencyService.complete(idempotency.record().id(), stored.id(), 201, "{}");
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "The stored response was recovered. Retry once more with the same key.");
        }
        if (!"USER".equals(stored.role()) || !content.equals(stored.content())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return stored.id();
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

    private static ChatMessageContextQuery emptyContextQuery() {
        return new ChatMessageContextQuery() {
            @Override
            public List<ChatConversationTurn> findRecentTurns(
                    UUID userId,
                    UUID sessionId,
                    UUID beforeMessageId,
                    int limit) {
                return List.of();
            }

            @Override
            public Optional<ChatMessage> findOwnedMessage(UUID userId, UUID sessionId, UUID messageId) {
                return Optional.empty();
            }
        };
    }
}
