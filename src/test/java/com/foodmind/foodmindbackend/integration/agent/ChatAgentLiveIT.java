package com.foodmind.foodmindbackend.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentCommand;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in live contract smoke test. Run with
 * CHAT_AGENT_LIVE_TEST=true, CHAT_AGENT_BASE_URL and INTERNAL_SERVICE_TOKEN,
 * then execute {@code ./mvnw -Plive-agent verify}.
 */
@EnabledIfEnvironmentVariable(named = "CHAT_AGENT_LIVE_TEST", matches = "(?i)true")
class ChatAgentLiveIT {

    @Test
    void liveAgentAcceptsBackendContract() {
        String baseUrl = requiredEnvironment("CHAT_AGENT_BASE_URL");
        String serviceToken = requiredEnvironment("INTERNAL_SERVICE_TOKEN");
        ChatAgentClientProperties properties = new ChatAgentClientProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setServiceToken(serviceToken);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(8));

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.getReadTimeout());
        ChatAgentHttpAdapter adapter = new ChatAgentHttpAdapter(
                RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build(),
                properties,
                JsonMapper.builder().build());

        ChatAgentCommand command = new ChatAgentCommand(
                properties.getContractVersion(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chat-live-profile",
                OffsetDateTime.now(),
                "live-delegation-smoke-token",
                "Where can I find saved records?",
                List.of());

        var result = adapter.generate(command);

        assertThat(result.successful()).isTrue();
        assertThat(result.contractVersion()).isEqualTo(properties.getContractVersion());
        assertThat(result.answer()).isNotBlank();
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the live-agent profile");
        }
        return value;
    }
}
