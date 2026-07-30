package com.foodmind.foodmindbackend.recommendation.application.port;

import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotSourceRow;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public interface TrainingSnapshotRowRepository {

    List<TrainingSnapshotSourceRow> rows(
            OffsetDateTime decisionFrom,
            OffsetDateTime decisionTo,
            OffsetDateTime observedThrough);
}
