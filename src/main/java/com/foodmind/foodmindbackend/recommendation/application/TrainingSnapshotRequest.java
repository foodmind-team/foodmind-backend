package com.foodmind.foodmindbackend.recommendation.application;

import java.nio.file.Path;
import java.time.OffsetDateTime;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record TrainingSnapshotRequest(
        OffsetDateTime decisionFrom,
        OffsetDateTime decisionTo,
        OffsetDateTime observedThrough,
        Path outputDirectory,
        String hmacSecret,
        String backendCommit,
        String provenance) {
}
