package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.recommendation.application.BuildTrainingSnapshot;
import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotRequest;
import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotResult;
import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotSourceRow;
import com.foodmind.foodmindbackend.recommendation.infrastructure.export.TrainingFeatureSchemaRegistry;
import com.foodmind.foodmindbackend.recommendation.infrastructure.export.TrainingSnapshotWriter;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrainingSnapshotExportTest extends PostgreSqlContainerSupport {

    private static final String HMAC_SECRET = "0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BuildTrainingSnapshot buildTrainingSnapshot;

    @Autowired
    private TrainingFeatureSchemaRegistry schemaRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE idempotency_record, recommendation_feedback, want_to_try, group_recommendation_share,
                    recommendation_candidate, recommendation_session, food_record, group_invitation, group_membership,
                    trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void exportContainsOnlyExplicitLabelsPseudonymsAndManifestMetadata() throws Exception {
        String accessToken = read(register("snapshot-primary@example.test", "Snapshot Primary"), "$.accessToken");
        MvcResult generated = generate(accessToken);
        String sessionId = read(generated, "$.sessionId");
        String candidateId = read(generated, "$.items[0].candidateId");
        String passiveCandidateId = read(generated, "$.items[1].candidateId");
        String mealId = read(generated, "$.items[0].mealId");
        String placeMealId = read(generated, "$.items[0].placeMealId");
        String userId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM app_user WHERE email = 'snapshot-primary@example.test'",
                String.class);

        submitFeedback(accessToken, sessionId, "snapshot-accept-key", """
                {
                  "candidateId": "%s",
                  "eventType": "ACCEPTED"
                }
                """.formatted(candidateId));
        submitFeedback(accessToken, sessionId, "snapshot-rating-key", """
                {
                  "candidateId": "%s",
                  "eventType": "LATER_RATED",
                  "rating": 4.5
                }
                """.formatted(candidateId));
        submitFeedback(accessToken, sessionId, "snapshot-would-key", """
                {
                  "candidateId": "%s",
                  "eventType": "WOULD_EAT_AGAIN",
                  "booleanValue": true
                }
                """.formatted(candidateId));

        TrainingSnapshotResult result = buildTrainingSnapshot.handle(new TrainingSnapshotRequest(
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(2),
                tempDir,
                HMAC_SECRET,
                "test-commit",
                "synthetic"));

        String rows = Files.readString(result.rowsPath());
        String manifest = Files.readString(result.manifestPath());
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.lrFeatureRowCount()).isEqualTo(1);
        assertThat(rows).contains("\"explicitLabel\":1");
        assertThat(rows).contains("\"laterRating\":4.5");
        assertThat(rows).contains("\"wouldEatAgain\":true");
        assertThat(rows).doesNotContain(userId, sessionId, candidateId, passiveCandidateId, mealId, placeMealId);
        assertThat(rows).doesNotContain("email", "comment", "rawFeatureSnapshot", "rawMealId", "rawOfferingId");
        assertThat(JsonPath.<Integer>read(manifest, "$.rowCount")).isEqualTo(1);
        assertThat(JsonPath.<String>read(manifest, "$.contentChecksum")).isEqualTo(result.contentChecksum());
        assertThat(JsonPath.<String>read(manifest, "$.backendCommit")).isEqualTo("test-commit");
    }

    @Test
    void featureRegistryFailsClosedForUnknownUnexpectedForbiddenWrongTypeAndOutOfRangeFeatures() {
        Map<String, Object> valid = validFeatures();
        assertThat(schemaRegistry.require("recommendation-features-v1", valid)).containsEntry("mealType", "DINNER");
        assertThatThrownBy(() -> schemaRegistry.require("unknown", valid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown feature schema");

        Map<String, Object> unexpected = validFeatures();
        unexpected.put("newScore", 1);
        assertThatThrownBy(() -> schemaRegistry.require("recommendation-features-v1", unexpected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected");

        Map<String, Object> forbidden = validFeatures();
        forbidden.put("context", Map.of("location", Map.of("latitude", 1.284), "comment", "secret"));
        assertThatThrownBy(() -> schemaRegistry.require("recommendation-features-v1", forbidden))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forbidden feature key");

        Map<String, Object> wrongType = validFeatures();
        wrongType.put("personalRecordCount", "3");
        assertThatThrownBy(() -> schemaRegistry.require("recommendation-features-v1", wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("personalRecordCount");

        Map<String, Object> outOfRange = validFeatures();
        outOfRange.put("distanceKm", 1001);
        assertThatThrownBy(() -> schemaRegistry.require("recommendation-features-v1", outOfRange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distanceKm");
    }

    @Test
    void nullFeatureRowsAreCollaborativeOnlyAndExcludedFromLrDataset() throws Exception {
        BuildTrainingSnapshot snapshot = new BuildTrainingSnapshot(
                (decisionFrom, decisionTo, observedThrough) -> List.of(new TrainingSnapshotSourceRow(
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        UUID.fromString("00000000-0000-4000-8000-000000000002"),
                        UUID.fromString("00000000-0000-4000-8000-000000000003"),
                        0,
                        OffsetDateTime.parse("2026-07-30T01:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        1,
                        "PERSONAL",
                        null,
                        null,
                        null,
                        "NOT_REQUESTED",
                        "fallback-v1",
                        "SUCCEEDED")),
                schemaRegistry,
                new TrainingSnapshotWriter(objectMapper, schemaRegistry),
                objectMapper);

        TrainingSnapshotResult result = snapshot.handle(new TrainingSnapshotRequest(
                OffsetDateTime.parse("2026-07-30T00:00:00Z"),
                OffsetDateTime.parse("2026-07-31T00:00:00Z"),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                tempDir.resolve("collaborative"),
                HMAC_SECRET,
                "test-commit",
                "synthetic"));

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.lrFeatureRowCount()).isZero();
        assertThat(result.collaborativeOnlyRowCount()).isEqualTo(1);
        assertThat(Files.readString(result.rowsPath())).contains("\"features\":null");
    }

    private Map<String, Object> validFeatures() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("mealType", "DINNER");
        features.put("cuisineCode", "INDIAN");
        features.put("area", "Tiong Bahru");
        features.put("priceAmount", 12.50);
        features.put("currency", "SGD");
        features.put("spiceLevel", 3);
        features.put("available", true);
        features.put("cleanlinessScore", 4.5);
        features.put("dietaryTagCodes", List.of("VEGETARIAN"));
        features.put("allergenCodes", List.of("SESAME"));
        features.put("wantToTry", false);
        features.put("personalRecordCount", 2);
        features.put("personalAverageRating", 4.5);
        features.put("groupRecordCount", 1);
        features.put("groupAverageRating", 5);
        features.put("distanceKm", 3.2);
        return features;
    }

    private void submitFeedback(String accessToken, String sessionId, String idempotencyKey, String body) throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/{sessionId}/feedback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private MvcResult generate(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "snapshot-generate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "maxBudget": 25,
                                  "currency": "SGD",
                                  "requestedFor": "2030-07-30T12:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult register(String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "correct horse battery",
                                  "clientType": "WEB",
                                  "deviceLabel": "JUnit"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
