package com.foodmind.foodmindbackend.analytics.application.port;

import com.foodmind.foodmindbackend.analytics.domain.AnalyticsWindow;
import com.foodmind.foodmindbackend.analytics.domain.DashboardProjection;
import java.util.Optional;
import java.util.UUID;

/**
 * @description: Read port for owner-scoped V10 dashboard projections.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

public interface DashboardQuery {

    Optional<String> userTimeZone(UUID actorId);

    DashboardProjection loadMetrics(UUID actorId, AnalyticsWindow window);
}
