package com.foodmind.foodmindbackend.recommendation.infrastructure.export;

import com.foodmind.foodmindbackend.recommendation.application.BuildTrainingSnapshot;
import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotRequest;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Component
public class TrainingSnapshotCommand implements ApplicationRunner {

    private final BuildTrainingSnapshot buildTrainingSnapshot;
    private final boolean enabled;
    private final String decisionFrom;
    private final String decisionTo;
    private final String observedThrough;
    private final String outputDirectory;
    private final String hmacSecret;
    private final String backendCommit;
    private final String provenance;

    public TrainingSnapshotCommand(
            BuildTrainingSnapshot buildTrainingSnapshot,
            @Value("${foodmind.training-snapshot.enabled:false}") boolean enabled,
            @Value("${foodmind.training-snapshot.decision-from:}") String decisionFrom,
            @Value("${foodmind.training-snapshot.decision-to:}") String decisionTo,
            @Value("${foodmind.training-snapshot.observed-through:}") String observedThrough,
            @Value("${foodmind.training-snapshot.output-directory:}") String outputDirectory,
            @Value("${foodmind.training-snapshot.hmac-secret:}") String hmacSecret,
            @Value("${foodmind.training-snapshot.backend-commit:unknown}") String backendCommit,
            @Value("${foodmind.training-snapshot.provenance:synthetic}") String provenance) {
        this.buildTrainingSnapshot = buildTrainingSnapshot;
        this.enabled = enabled;
        this.decisionFrom = decisionFrom;
        this.decisionTo = decisionTo;
        this.observedThrough = observedThrough;
        this.outputDirectory = outputDirectory;
        this.hmacSecret = hmacSecret;
        this.backendCommit = backendCommit;
        this.provenance = provenance;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        buildTrainingSnapshot.handle(new TrainingSnapshotRequest(
                OffsetDateTime.parse(decisionFrom),
                OffsetDateTime.parse(decisionTo),
                OffsetDateTime.parse(observedThrough),
                Path.of(outputDirectory),
                hmacSecret,
                backendCommit,
                provenance));
    }
}
