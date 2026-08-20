package com.foodmind.foodmindbackend.chat.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentGenerationResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentSourceResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatAgentResultValidatorTest {

    private final ChatAgentResultValidator validator = new ChatAgentResultValidator();

    @Test
    void unsupportedResponsesMustNotContainSources() {
        UUID requestId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ChatAgentGenerationResult result = ChatAgentGenerationResult.success(
                "chat-agent-v2",
                requestId,
                sessionId,
                messageId,
                "trace",
                "agent-trace",
                ChatResponseStatus.UNSUPPORTED,
                "Outside FoodMind scope.",
                List.of(new ChatAgentSourceResult(
                        ChatSourceType.PLACE,
                        UUID.randomUUID(),
                        1,
                        java.util.Map.of())));

        assertThatThrownBy(() -> validator.validate(requestId, sessionId, messageId, "trace", result))
                .isInstanceOf(ChatAgentResultValidator.ChatAgentValidationException.class)
                .hasMessageContaining("Unsupported answers must not cite sources");
    }
}
