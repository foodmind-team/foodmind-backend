package com.foodmind.foodmindbackend.analytics.domain;

import java.util.List;

/**
 * @description: Complete dashboard read projection for one owner and bounded period.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

public record DashboardProjection(AnalyticsWindow window, List<MetricValue> metrics) {

    public DashboardProjection {
        metrics = List.copyOf(metrics);
    }

    public boolean empty() {
        return metrics.isEmpty();
    }
}
