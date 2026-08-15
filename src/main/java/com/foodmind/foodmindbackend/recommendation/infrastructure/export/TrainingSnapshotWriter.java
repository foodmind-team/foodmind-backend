package com.foodmind.foodmindbackend.recommendation.infrastructure.export;

import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotOutputRow;
import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotRequest;
import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Component
public class TrainingSnapshotWriter {

    private final ObjectMapper objectMapper;
    private final TrainingFeatureSchemaRegistry schemaRegistry;

    public TrainingSnapshotWriter(ObjectMapper objectMapper, TrainingFeatureSchemaRegistry schemaRegistry) {
        this.objectMapper = objectMapper;
        this.schemaRegistry = schemaRegistry;
    }

    public TrainingSnapshotResult write(
            TrainingSnapshotRequest request,
            List<TrainingSnapshotOutputRow> rows,
            String featureAllowListChecksum) {
        try {
            Files.createDirectories(request.outputDirectory());
            String content = rowsContent(rows);
            String checksum = sha256(content);
            int lrRows = (int) rows.stream().filter(row -> row.features() != null).count();
            int collaborativeRows = rows.size() - lrRows;
            Path rowsPath = request.outputDirectory().resolve("training-snapshot.ndjson");
            Path manifestPath = request.outputDirectory().resolve("manifest.json");
            Files.writeString(rowsPath, content, StandardCharsets.UTF_8);
            Files.writeString(
                    manifestPath,
                    toJson(manifest(request, rows, checksum, featureAllowListChecksum, lrRows, collaborativeRows)),
                    StandardCharsets.UTF_8);
            return new TrainingSnapshotResult(rowsPath, manifestPath, rows.size(), lrRows, collaborativeRows, checksum);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write training snapshot.", exception);
        }
    }

    private String rowsContent(List<TrainingSnapshotOutputRow> rows) {
        return rows.stream()
                .map(this::toJson)
                .reduce("", (left, right) -> left + right + "\n");
    }

    private Map<String, Object> manifest(
            TrainingSnapshotRequest request,
            List<TrainingSnapshotOutputRow> rows,
            String checksum,
            String featureAllowListChecksum,
            int lrRows,
            int collaborativeRows) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("backendCommit", request.backendCommit() == null ? "unknown" : request.backendCommit());
        manifest.put("exportSchemaVersion", "foodmind-training-snapshot-v2");
        manifest.put("featureSchemaVersion", schemaRegistry.featureSchemaVersion());
        manifest.put("featureAllowListVersion", schemaRegistry.featureAllowListVersion());
        manifest.put("featureAllowListChecksum", featureAllowListChecksum);
        manifest.put("decisionFrom", request.decisionFrom());
        manifest.put("decisionTo", request.decisionTo());
        manifest.put("observedThrough", request.observedThrough());
        manifest.put("observationCutoffExclusive", true);
        manifest.put("rowCount", rows.size());
        manifest.put("lrFeatureRowCount", lrRows);
        manifest.put("collaborativeOnlyRowCount", collaborativeRows);
        manifest.put("collaborativeSignal", Map.of(
                "name", "collaborativeStrength",
                "positiveOnly", true,
                "range", "[0,1]",
                "rejectionsIncluded", false));
        manifest.put("contentChecksum", checksum);
        manifest.put("provenance", request.provenance() == null ? "synthetic" : request.provenance());
        manifest.put("laterSignalTimestamps", rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.laterRatingCreatedAt(), row.wouldEatAgainCreatedAt()))
                .filter(java.util.Objects::nonNull)
                .map(OffsetDateTime::toString)
                .sorted()
                .toList());
        return manifest;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM.", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise training snapshot.", exception);
        }
    }
}
