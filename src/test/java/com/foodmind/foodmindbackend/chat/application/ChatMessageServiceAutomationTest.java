package com.foodmind.foodmindbackend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmind.foodmindbackend.chat.application.port.ChatAgentPort;
import com.foodmind.foodmindbackend.chat.application.port.ChatMessageContextQuery;
import com.foodmind.foodmindbackend.chat.application.port.ChatReferenceQuery;
import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.ChatSession;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentGenerationResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatConversationTurn;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyAttempt;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyRecord;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatMessageServiceAutomationTest {

    @Test
    void completedIdempotentRetryReturnsStoredAssistantWithoutCallingAgentAgain() {
        Fixture fixture = new Fixture();
        UUID recordId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        ChatMessage assistant = message(assistantId, fixture.sessionId, "ASSISTANT", "Stored answer");
        when(fixture.idempotencyService.sha256Hex(anyString())).thenReturn("hash");
        when(fixture.idempotencyService.beginAttempt(any(), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyAttempt(
                        new IdempotencyRecord(recordId, "COMPLETED", "hash", assistantId),
                        false));
        when(fixture.contextQuery.findOwnedMessage(fixture.userId, fixture.sessionId, assistantId))
                .thenReturn(Optional.of(assistant));

        ChatMessage result = fixture.service().post(
                fixture.userId,
                fixture.sessionId,
                "Explain tofu",
                List.of(),
                false,
                null,
                "stable-key");

        assertThat(result).isSameAs(assistant);
        verify(fixture.agentPort, never()).generate(any());
        verify(fixture.transactionService, never()).beginMessage(any(), any(), anyString(), any());
    }

    @Test
    void activeDuplicateReturnsConflictInsteadOfCreatingAnotherUserMessage() {
        Fixture fixture = new Fixture();
        when(fixture.idempotencyService.sha256Hex(anyString())).thenReturn("hash");
        when(fixture.idempotencyService.beginAttempt(any(), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyAttempt(
                        new IdempotencyRecord(UUID.randomUUID(), "IN_PROGRESS", "hash", null),
                        false));

        assertThatThrownBy(() -> fixture.service().post(
                fixture.userId,
                fixture.sessionId,
                "Explain tofu",
                List.of(),
                false,
                null,
                "stable-key"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.safeMessage()).contains("still being processed");
                });
        verify(fixture.transactionService, never()).beginMessage(any(), any(), anyString(), any());
    }

    @Test
    void recentTurnsAndValidatedSuggestionsReachTheAgentAndLiveResponse() {
        Fixture fixture = new Fixture();
        UUID userMessageId = UUID.randomUUID();
        List<ChatConversationTurn> turns = List.of(
                new ChatConversationTurn("USER", "How much protein is in tofu?"),
                new ChatConversationTurn("ASSISTANT", "It varies by firmness."));
        when(fixture.contextQuery.findRecentTurns(fixture.userId, fixture.sessionId, null, 8))
                .thenReturn(turns);
        when(fixture.transactionService.beginMessage(any(), any(), anyString(), any())).thenReturn(userMessageId);
        when(fixture.delegationTokenIssuer.issue(any(), anyString(), any(), any()))
                .thenReturn(new DelegationTokenIssuer.IssuedDelegationToken("delegation", OffsetDateTime.now().plusMinutes(2)));
        AtomicReference<ChatAgentCommand> capturedCommand = new AtomicReference<>();
        when(fixture.agentPort.generate(any())).thenAnswer(invocation -> {
            ChatAgentCommand command = invocation.getArgument(0);
            capturedCommand.set(command);
            return ChatAgentGenerationResult.success(
                    command.contractVersion(),
                    command.requestId(),
                    command.sessionId(),
                    command.userMessageId(),
                    command.traceId(),
                    "agent-trace",
                    ChatRoute.SUMMARY,
                    ChatResponseStatus.SUCCEEDED,
                    "Tempeh is another useful comparison.",
                    List.of(),
                    List.of("Compare tofu and tempeh."),
                    List.of("EXPLORE"));
        });
        ChatMessage stored = message(UUID.randomUUID(), fixture.sessionId, "ASSISTANT", "Tempeh answer")
                .withSuggestions(List.of("Compare tofu and tempeh."), List.of("EXPLORE"));
        when(fixture.transactionService.completeGroundedMessage(any(), any(), any(), any())).thenReturn(stored);

        ChatMessage result = fixture.service().post(
                fixture.userId,
                fixture.sessionId,
                "What about tempeh?",
                List.of(),
                false,
                null);

        assertThat(capturedCommand.get().recentTurns()).isEqualTo(turns);
        ArgumentCaptor<List<String>> scopes = ArgumentCaptor.forClass(List.class);
        verify(fixture.delegationTokenIssuer).issue(any(), anyString(), scopes.capture(), any());
        assertThat(scopes.getValue()).containsExactly(
                DelegationTokenIssuer.SCOPE_CHAT_SEARCH,
                DelegationTokenIssuer.SCOPE_CHAT_REFERENCE_RESOLVE,
                DelegationTokenIssuer.SCOPE_CHAT_PROFILE);
        assertThat(result.suggestedQuestions()).containsExactly("Compare tofu and tempeh.");
        assertThat(result.suggestedDestinations()).containsExactly("EXPLORE");
        ArgumentCaptor<ValidatedChatAgentResult> validated = ArgumentCaptor.forClass(ValidatedChatAgentResult.class);
        verify(fixture.transactionService).completeGroundedMessage(
                any(), any(), any(), validated.capture());
        assertThat(validated.getValue().suggestedQuestions()).containsExactly("Compare tofu and tempeh.");
    }

    private static ChatMessage message(UUID id, UUID sessionId, String role, String content) {
        return new ChatMessage(
                id,
                sessionId,
                role,
                content,
                role.equals("ASSISTANT") ? ChatRoute.SUMMARY : null,
                role.equals("ASSISTANT") ? ChatResponseStatus.SUCCEEDED : null,
                UUID.randomUUID(),
                role.equals("ASSISTANT") ? "agent" : null,
                OffsetDateTime.now(),
                List.of());
    }

    private static final class Fixture {
        private final UUID userId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final ChatRepository repository = mock(ChatRepository.class);
        private final ChatReferenceQuery referenceQuery = mock(ChatReferenceQuery.class);
        private final ChatMessageContextQuery contextQuery = mock(ChatMessageContextQuery.class);
        private final ChatTransactionService transactionService = mock(ChatTransactionService.class);
        private final ChatAgentPort agentPort = mock(ChatAgentPort.class);
        private final DelegationTokenIssuer delegationTokenIssuer = mock(DelegationTokenIssuer.class);
        private final IdempotencyService idempotencyService = mock(IdempotencyService.class);

        private Fixture() {
            when(repository.findOwnedSession(userId, sessionId))
                    .thenReturn(Optional.of(new ChatSession(sessionId, "Chat", "ACTIVE", null, null)));
            when(contextQuery.findRecentTurns(userId, sessionId, null, 8)).thenReturn(List.of());
        }

        private ChatMessageService service() {
            return new ChatMessageService(
                    repository,
                    referenceQuery,
                    contextQuery,
                    transactionService,
                    agentPort,
                    delegationTokenIssuer,
                    idempotencyService,
                    new SimpleMeterRegistry());
        }
    }
}
