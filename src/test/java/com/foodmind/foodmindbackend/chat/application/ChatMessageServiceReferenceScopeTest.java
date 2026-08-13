package com.foodmind.foodmindbackend.chat.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmind.foodmindbackend.chat.application.port.ChatAgentPort;
import com.foodmind.foodmindbackend.chat.application.port.ChatReferenceQuery;
import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatSession;
import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageServiceReferenceScopeTest {

    @Test
    void explicitFalseWithEmptyIdsDoesNotInheritSessionReferences() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatRepository repository = mock(ChatRepository.class);
        ChatReferenceQuery referenceQuery = mock(ChatReferenceQuery.class);
        RuntimeException stopAfterScopeResolution = new RuntimeException("scope captured");
        when(repository.findOwnedSession(userId, sessionId))
                .thenReturn(Optional.of(new ChatSession(sessionId, "Chat", "ACTIVE", null, null)));
        when(referenceQuery.resolveSessionReferences(userId, sessionId, List.of()))
                .thenThrow(stopAfterScopeResolution);
        ChatMessageService service = service(repository, referenceQuery);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.post(userId, sessionId, "No sources", List.of(), false, null));

        assertSame(stopAfterScopeResolution, thrown);
        verify(repository, never()).findSessionReferences(userId, sessionId);
        verify(referenceQuery).resolveSessionReferences(userId, sessionId, List.of());
    }

    @Test
    void omittedFlagPreservesLegacySessionInheritance() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatRepository repository = mock(ChatRepository.class);
        ChatReferenceQuery referenceQuery = mock(ChatReferenceQuery.class);
        RuntimeException stopAfterScopeResolution = new RuntimeException("scope captured");
        when(repository.findOwnedSession(userId, sessionId))
                .thenReturn(Optional.of(new ChatSession(sessionId, "Chat", "ACTIVE", null, null)));
        when(repository.findSessionReferences(userId, sessionId)).thenReturn(List.of());
        when(referenceQuery.resolveSessionReferences(userId, sessionId, List.of()))
                .thenThrow(stopAfterScopeResolution);
        ChatMessageService service = service(repository, referenceQuery);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.post(userId, sessionId, "Legacy client", List.of(), null, null));

        assertSame(stopAfterScopeResolution, thrown);
        verify(repository).findSessionReferences(userId, sessionId);
    }

    private ChatMessageService service(ChatRepository repository, ChatReferenceQuery referenceQuery) {
        return new ChatMessageService(
                repository,
                referenceQuery,
                mock(ChatTransactionService.class),
                mock(ChatAgentPort.class),
                mock(DelegationTokenIssuer.class),
                new SimpleMeterRegistry());
    }
}
