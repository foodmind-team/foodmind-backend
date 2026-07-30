package com.foodmind.foodmindbackend.analytics.api.response;

import com.foodmind.foodmindbackend.analytics.domain.WeeklyRecapProjection;
import java.time.LocalDate;
import java.util.List;

/**
 * @description: Public weekly recap contract with explicit empty-state metadata.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:35 pm
 */

public record WeeklyRecapResponse(
        LocalDate weekStart,
        String timeZone,
        boolean empty,
        List<DashboardResponse.MetricResponse> metrics,
        List<DashboardResponse.MetricResponse> spendingTotals) {

    public static WeeklyRecapResponse from(WeeklyRecapProjection projection) {
        List<DashboardResponse.MetricResponse> metrics = projection.metrics().stream()
                .map(metric -> new DashboardResponse.MetricResponse(metric.code(), metric.label(), metric.period(),
                        metric.value(), metric.unit(), metric.currency(), metric.samples(), metric.denominator(),
                        metric.empty(), metric.dimension(), metric.dimensionLabel()))
                .toList();
        return new WeeklyRecapResponse(projection.weekStart(), projection.timeZone(), projection.empty(), metrics,
                metrics.stream().filter(metric -> "SPENDING_TOTAL".equals(metric.code())).toList());
    }
}
