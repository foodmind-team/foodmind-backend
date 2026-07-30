package com.foodmind.foodmindbackend.recommendation.application;

import java.nio.file.Path;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public record TrainingSnapshotResult(
        Path rowsPath,
        Path manifestPath,
        int rowCount,
        int lrFeatureRowCount,
        int collaborativeOnlyRowCount,
        String contentChecksum) {
}
