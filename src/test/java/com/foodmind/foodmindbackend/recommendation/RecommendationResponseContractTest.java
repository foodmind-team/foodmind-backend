package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.recommendation.api.response.RecommendationResponse;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionProfile;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionMode;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RecommendationResponseContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-11T12:00:00Z");

    @Test
    void openApiEnumsAndNullabilityCoverEveryPersistedRecommendationState() throws IOException {
        Map<String, Object> schema = recommendationSchema();
        Map<String, Object> properties = map(schema.get("properties"));

        assertThat(enumValues(properties, "status"))
                .containsExactlyInAnyOrder(
                        "CREATED", "PROCESSING", "SUCCEEDED", "FALLBACK_SUCCEEDED", "NO_VALID_CANDIDATE", "FAILED");
        assertThat(enumValues(properties, "modelStatus"))
                .containsExactlyInAnyOrder(
                        "NOT_REQUESTED", "PENDING", "SUCCEEDED", "INSUFFICIENT_DATA", "UNAVAILABLE",
                        "TIMED_OUT", "INVALID_RESPONSE", "FAILED");
        assertThat(enumValues(properties, "fallbackStatus"))
                .containsExactlyInAnyOrder("NOT_STARTED", "NOT_REQUIRED", "SUCCEEDED", "NO_VALID_CANDIDATE", "FAILED");

        assertThat(map(properties.get("modelVersion"))).containsEntry("nullable", true);
        assertThat(map(properties.get("fallbackVersion"))).containsEntry("nullable", true);
        assertThat(map(properties.get("completedAt"))).containsEntry("nullable", true);
        assertThat(properties).containsKey("decisionProfile");
        assertThat(list(schema.get("required"))).contains("decisionProfile");
        assertThat(list(schema.get("required"))).doesNotContain("modelVersion", "fallbackVersion", "completedAt");
    }

    @ParameterizedTest(name = "serialises public recommendation state {0}")
    @MethodSource("reachableStates")
    void serialisedResponseMatchesTheDocumentedStateRules(
            String status,
            String modelStatus,
            String modelVersion,
            String fallbackStatus,
            String fallbackVersion,
            boolean completed) throws Exception {
        RecommendationResult result = new RecommendationResult(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                "trace-contract",
                status,
                modelStatus,
                modelVersion,
                fallbackStatus,
                fallbackVersion,
                NOW,
                completed ? NOW.plusSeconds(1) : null,
                new RecommendationDecisionProfile(RecommendationDecisionMode.DEFAULT, List.of(), 0),
                List.of());

        JsonNode json = JSON.readTree(JSON.writeValueAsString(RecommendationResponse.from(result)));
        assertThat(json.path("status").asText()).isEqualTo(status);
        assertThat(json.path("modelStatus").asText()).isEqualTo(modelStatus);
        assertThat(json.path("fallbackStatus").asText()).isEqualTo(fallbackStatus);
        assertThat(textOrNull(json, "modelVersion")).isEqualTo(modelVersion);
        assertThat(textOrNull(json, "fallbackVersion")).isEqualTo(fallbackVersion);
        assertThat(json.path("createdAt").asText()).isNotBlank();
        assertThat(textOrNull(json, "completedAt") != null).isEqualTo(completed);
        assertThat(json.path("items").isArray()).isTrue();
        assertThat(json.path("decisionProfile").path("mode").asText()).isEqualTo("DEFAULT");
    }

    private static Stream<Arguments> reachableStates() {
        return Stream.of(
                Arguments.of("CREATED", "NOT_REQUESTED", null, "NOT_STARTED", null, false),
                Arguments.of("PROCESSING", "PENDING", null, "NOT_STARTED", null, false),
                Arguments.of("SUCCEEDED", "SUCCEEDED", "model-v1", "NOT_REQUIRED", null, true),
                Arguments.of("FALLBACK_SUCCEEDED", "UNAVAILABLE", null, "SUCCEEDED", "fallback-v1", true),
                Arguments.of("NO_VALID_CANDIDATE", "FAILED", null, "NO_VALID_CANDIDATE", "fallback-v1", true),
                Arguments.of("FAILED", "FAILED", null, "FAILED", "fallback-v1", true));
    }

    private static Map<String, Object> recommendationSchema() throws IOException {
        try (InputStream input = RecommendationResponseContractTest.class
                .getResourceAsStream("/openapi/openapi.yaml")) {
            assertThat(input).as("canonical OpenAPI resource").isNotNull();
            Map<String, Object> document = new Yaml().load(input);
            return map(map(map(document.get("components")).get("schemas")).get("RecommendationResponse"));
        }
    }

    private static Set<String> enumValues(Map<String, Object> properties, String property) {
        return Set.copyOf(list(map(properties.get(property)).get("enum")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object value) {
        return (List<String>) value;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
