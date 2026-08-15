package com.foodmind.foodmindbackend.recommendation.application;

import com.foodmind.foodmindbackend.recommendation.application.port.TrainingSnapshotRowRepository;
import com.foodmind.foodmindbackend.recommendation.infrastructure.export.TrainingFeatureSchemaRegistry;
import com.foodmind.foodmindbackend.recommendation.infrastructure.export.TrainingSnapshotWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Service
public class BuildTrainingSnapshot {

    private final TrainingSnapshotRowRepository rowRepository;
    private final TrainingFeatureSchemaRegistry schemaRegistry;
    private final TrainingSnapshotWriter writer;
    private final ObjectMapper objectMapper;

    public BuildTrainingSnapshot(
            TrainingSnapshotRowRepository rowRepository,
            TrainingFeatureSchemaRegistry schemaRegistry,
            TrainingSnapshotWriter writer,
            ObjectMapper objectMapper) {
        this.rowRepository = rowRepository;
        this.schemaRegistry = schemaRegistry;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TrainingSnapshotResult handle(TrainingSnapshotRequest request) {
        validateRequest(request);
        List<TrainingSnapshotSourceRow> sourceRows = rowRepository.rows(
                request.decisionFrom(),
                request.decisionTo(),
                request.observedThrough());
        List<TrainingSnapshotOutputRow> outputRows = sourceRows.stream()
                .map(row -> outputRow(request.hmacSecret(), row))
                .toList();
        return writer.write(request, outputRows, schemaRegistry.featureAllowListChecksum());
    }

    private TrainingSnapshotOutputRow outputRow(String hmacSecret, TrainingSnapshotSourceRow source) {
        Object features = source.rawFeatureSnapshot() == null
                ? null
                : schemaRegistry.require(source.featureSchemaVersion(), featureMap(source.rawFeatureSnapshot()));
        return new TrainingSnapshotOutputRow(
                hmac(hmacSecret, "user:" + source.userId()),
                hmac(hmacSecret, "meal:" + source.rawMealId()),
                hmac(hmacSecret, "offering:" + source.rawOfferingId()),
                source.decisionCreatedAt(),
                source.explicitLabel(),
                source.laterRating(),
                source.laterRatingCreatedAt(),
                source.wouldEatAgain(),
                source.wouldEatAgainCreatedAt(),
                collaborativeStrength(source),
                features,
                source.featureSchemaVersion(),
                source.candidateRank(),
                source.candidateType(),
                source.modelVersion(),
                source.modelStatus(),
                source.fallbackVersion(),
                source.fallbackStatus());
    }

    /**
     * A privacy-preserving, positive-only interaction signal for the offline
     * collaborative-filtering job. Rejections and passive impressions must
     * never be converted into implicit negative interactions.
     */
    private double collaborativeStrength(TrainingSnapshotSourceRow source) {
        double strength = source.explicitLabel() == 1 ? 1.0 : 0.0;
        if (source.laterRating() != null && source.laterRating().compareTo(java.math.BigDecimal.valueOf(4)) >= 0) {
            strength = Math.max(strength, source.laterRating().doubleValue() / 5.0);
        }
        if (Boolean.TRUE.equals(source.wouldEatAgain())) {
            strength = Math.max(strength, 1.0);
        }
        return Math.min(1.0, strength);
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> featureMap(String rawFeatureSnapshot) {
        try {
            return objectMapper.readValue(rawFeatureSnapshot, java.util.Map.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Raw feature snapshot is not a JSON object.", exception);
        }
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is required by the JVM.", exception);
        }
    }

    private void validateRequest(TrainingSnapshotRequest request) {
        if (request.decisionFrom() == null
                || request.decisionTo() == null
                || request.observedThrough() == null
                || !request.decisionFrom().isBefore(request.decisionTo())
                || request.decisionTo().isAfter(request.observedThrough())) {
            throw new IllegalArgumentException("Invalid decision window or observation cutoff.");
        }
        if (request.outputDirectory() == null) {
            throw new IllegalArgumentException("Output directory is required.");
        }
        if (request.hmacSecret() == null || request.hmacSecret().length() < 32) {
            throw new IllegalArgumentException("HMAC secret must be at least 32 characters.");
        }
    }
}
