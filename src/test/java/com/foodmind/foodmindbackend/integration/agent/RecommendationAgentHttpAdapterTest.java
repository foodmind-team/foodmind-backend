package com.foodmind.foodmindbackend.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

class RecommendationAgentHttpAdapterTest {

    @Test
    void sendsPrivateAuthenticationAndCorrelationHeaders() throws Exception {
        RecommendationAgentCommand command = command();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> correlation = new AtomicReference<>();
        try (TestServer server = TestServer.start(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            respond(exchange, HttpStatus.OK.value(), successfulResponse(command));
        })) {
            AgentGenerationResult result = adapter(server, 4096).generate(command);

            assertThat(result.successful()).isTrue();
            assertThat(result.modelVersion()).isEqualTo("recommendation-agent-demo-2026-07-30");
            assertThat(authorization).hasValue("Bearer test-service-token");
            assertThat(correlation).hasValue(command.traceId());
        }
    }

    @Test
    void mapsNonTwoHundredWithoutExposingPayload() throws Exception {
        try (TestServer server = TestServer.start(exchange ->
                respond(exchange, HttpStatus.INTERNAL_SERVER_ERROR.value(), "{\"secret\":\"raw-upstream\"}"))) {
            AgentGenerationResult result = adapter(server, 4096).generate(command());

            assertThat(result.successful()).isFalse();
            assertThat(result.failureCode()).isEqualTo(AgentFailureCode.NON_2XX);
        }
    }

    @Test
    void rejectsOversizedPayloadBeforeParsing() throws Exception {
        try (TestServer server = TestServer.start(exchange ->
                respond(exchange, HttpStatus.OK.value(), successfulResponse(command())))) {
            AgentGenerationResult result = adapter(server, 20).generate(command());

            assertThat(result.successful()).isFalse();
            assertThat(result.failureCode()).isEqualTo(AgentFailureCode.OVERSIZED_RESPONSE);
        }
    }

    private RecommendationAgentHttpAdapter adapter(TestServer server, int maxResponseBytes) {
        AgentClientProperties properties = new AgentClientProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(server.baseUrl());
        properties.setServiceToken("test-service-token");
        properties.setConnectTimeout(Duration.ofMillis(250));
        properties.setReadTimeout(Duration.ofMillis(800));
        properties.setMaxResponseBytes(maxResponseBytes);
        return new RecommendationAgentHttpAdapter(
                RestClient.builder().baseUrl(server.baseUrl()).build(),
                properties,
                JsonMapper.builder().build());
    }

    private RecommendationAgentCommand command() {
        UUID candidateId = UUID.fromString("30000000-0000-4000-8000-000000000101");
        return new RecommendationAgentCommand(
                "recommendation-agent-v1",
                UUID.fromString("30000000-0000-4000-8000-000000000001"),
                UUID.fromString("30000000-0000-4000-8000-000000000002"),
                "30000000-0000-4000-8000-000000000003",
                OffsetDateTime.parse("2030-07-30T12:00:02Z"),
                Map.of("mealType", "DINNER"),
                Map.of("currency", "SGD"),
                List.of(new RecommendationAgentCandidate(
                        candidateId,
                        UUID.randomUUID(),
                        evidence(),
                        Map.of("priceAmount", new BigDecimal("9.50")))));
    }

    private CandidateEvidence evidence() {
        return new CandidateEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Fixture Meal",
                "DINNER",
                "INDIAN",
                UUID.randomUUID(),
                "Fixture Place",
                "Serangoon",
                new BigDecimal("1.349600"),
                new BigDecimal("103.873700"),
                new MoneyAmount(new BigDecimal("9.50"), "SGD"),
                2,
                true,
                new CleanlinessEvidence(new BigDecimal("0.90"), OffsetDateTime.parse("2030-07-01T00:00:00Z"), "CURATED_DEMO"),
                List.of(),
                List.of(),
                false,
                0,
                null,
                null,
                0,
                null,
                null,
                null);
    }

    private String successfulResponse(RecommendationAgentCommand command) {
        return """
                {
                  "contractVersion": "%s",
                  "requestId": "%s",
                  "sessionId": "%s",
                  "traceId": "%s",
                  "agentTraceId": "agent-trace-http-test",
                  "status": "SUCCEEDED",
                  "modelVersion": "recommendation-agent-demo-2026-07-30",
                  "featureSchemaVersion": "recommendation-features-v1",
                  "candidates": [
                    {
                      "candidateId": "%s",
                      "rank": 1,
                      "recommendationType": "PERSONAL",
                      "modelScore": 0.8700000,
                      "reasonCodes": ["WITHIN_BUDGET"],
                      "explanation": "Inside the requested budget.",
                      "featureSnapshot": {}
                    }
                  ]
                }
                """.formatted(
                command.contractVersion(),
                command.requestId(),
                command.sessionId(),
                command.traceId(),
                command.candidates().get(0).candidateId());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record TestServer(HttpServer server, String baseUrl) implements AutoCloseable {

        static TestServer start(ThrowingExchangeHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/internal/v1/recommendations/generate", exchange -> handler.handle(exchange));
            server.start();
            return new TestServer(server, "http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ThrowingExchangeHandler {

        void handle(HttpExchange exchange) throws IOException;
    }
}
