package com.foodmind.foodmindbackend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatTransactionServiceTest {

    @Test
    void bindsTheUserMessageAndCompletesTheAssistantWithinTheTransactionService() {
        ChatRepository repository = mock(ChatRepository.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        ChatTransactionService service = new ChatTransactionService(repository, idempotencyService);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        when(repository.insertUserMessage(userId, sessionId, "Explain tofu", correlationId))
                .thenReturn(userMessageId);

        assertThat(service.beginMessage(userId, sessionId, "Explain tofu", correlationId, recordId))
                .isEqualTo(userMessageId);
        verify(idempotencyService).associateResource(recordId, userMessageId);

        ValidatedChatAgentResult result = new ValidatedChatAgentResult(
                "chat-agent-v1",
                "agent-trace",
                ChatRoute.SUMMARY,
                ChatResponseStatus.SUCCEEDED,
                "Tofu is a useful protein source.",
                List.of(),
                List.of("Compare tofu and tempeh."),
                List.of("EXPLORE"));
        ChatMessage stored = assistantMessage(
                assistantMessageId,
                sessionId,
                ChatResponseStatus.SUCCEEDED,
                "Tofu is a useful protein source.");
        when(repository.insertAssistantMessage(userId, sessionId, userMessageId, result)).thenReturn(stored);

        ChatMessage response = service.completeGroundedMessage(
                userId,
                sessionId,
                userMessageId,
                result,
                recordId);

        verify(idempotencyService).complete(recordId, assistantMessageId, 201, "{}");
        assertThat(response.suggestedQuestions()).containsExactly("Compare tofu and tempeh.");
        assertThat(response.suggestedDestinations()).containsExactly("EXPLORE");
    }

    @Test
    void completesTheIdempotencyRecordWhenAStoredAssistantMarksFailure() {
        ChatRepository repository = mock(ChatRepository.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        ChatTransactionService service = new ChatTransactionService(repository, idempotencyService);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        ChatMessage failed = assistantMessage(
                assistantMessageId,
                sessionId,
                ChatResponseStatus.FAILED,
                "The assistant could not complete this response.");
        when(repository.insertFailedAssistantMessage(userId, sessionId, userMessageId, "trace-id"))
                .thenReturn(failed);

        ChatMessage response = service.markFailed(userId, sessionId, userMessageId, "trace-id", recordId);

        assertThat(response).isSameAs(failed);
        verify(idempotencyService).complete(recordId, assistantMessageId, 201, "{}");
    }

    private ChatMessage assistantMessage(
            UUID id,
            UUID sessionId,
            ChatResponseStatus status,
            String content) {
        return new ChatMessage(
                id,
                sessionId,
                "ASSISTANT",
                content,
                ChatRoute.SUMMARY,
                status,
                UUID.randomUUID(),
                "agent-trace",
                OffsetDateTime.now(),
                List.of());
    }
}
