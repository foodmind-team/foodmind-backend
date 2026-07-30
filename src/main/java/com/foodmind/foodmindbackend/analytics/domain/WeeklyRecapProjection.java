package com.foodmind.foodmindbackend.analytics.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * @description: Concise weekly recap projection distinct from live dashboard interactions.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:35 pm
 */

public record WeeklyRecapProjection(
        LocalDate weekStart,
        String timeZone,
        List<MetricValue> metrics) {

    public WeeklyRecapProjection {
        metrics = List.copyOf(metrics);
    }

    public boolean empty() {
        return metrics.isEmpty();
    }
}
