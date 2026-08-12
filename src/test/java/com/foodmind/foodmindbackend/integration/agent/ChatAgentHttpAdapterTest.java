package com.foodmind.foodmindbackend.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentChatRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ChatAgentHttpAdapterTest {

    @Test
    void unavailableAgentTreatsEmptyContextAsSupportedNavigation() {
        ChatAgentClientProperties properties = new ChatAgentClientProperties();
        properties.setEnabled(false);
        ChatAgentHttpAdapter adapter = new ChatAgentHttpAdapter(
                RestClient.builder().build(), properties, JsonMapper.builder().build());

        var result = adapter.generate(command("Where can I find my saved food records?"));

        assertThat(result.successful()).isTrue();
        assertThat(result.route()).isEqualTo(ChatRoute.NAVIGATION);
        assertThat(result.responseStatus()).isEqualTo(ChatResponseStatus.FALLBACK_SUCCEEDED);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void unavailableAgentKeepsRecommendationRequestsOutOfScope() {
        ChatAgentClientProperties properties = new ChatAgentClientProperties();
        properties.setEnabled(false);
        ChatAgentHttpAdapter adapter = new ChatAgentHttpAdapter(
                RestClient.builder().build(), properties, JsonMapper.builder().build());

        var result = adapter.generate(command("Recommend a dinner for me."));

        assertThat(result.route()).isEqualTo(ChatRoute.OUT_OF_SCOPE);
        assertThat(result.responseStatus()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    @Test
    void privateRequestCarriesRequestedRouteAndDeadline() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2030-07-30T12:00:00Z");
        ChatAgentCommand command = new ChatAgentCommand(
                "chat-agent-v1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chat-adapter-test",
                expiresAt,
                "delegation-token",
                ChatRoute.SEARCH,
                "Find oat drinks",
                List.of());

        AgentChatRequest request = AgentChatRequest.from(command);

        assertThat(request.expiresAt()).isEqualTo(expiresAt);
        assertThat(request.requestedRoute()).isEqualTo(ChatRoute.SEARCH);
    }

    private ChatAgentCommand command(String message) {
        return new ChatAgentCommand(
                "chat-agent-v1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chat-adapter-test",
                OffsetDateTime.parse("2030-07-30T12:00:00Z"),
                "delegation-token",
                null,
                message,
                List.of());
    }
}
