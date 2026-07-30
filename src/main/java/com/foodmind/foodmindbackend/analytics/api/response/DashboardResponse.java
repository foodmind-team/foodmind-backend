package com.foodmind.foodmindbackend.analytics.api.response;

import com.foodmind.foodmindbackend.analytics.domain.DashboardProjection;
import com.foodmind.foodmindbackend.analytics.domain.DashboardGroupBy;
import com.foodmind.foodmindbackend.analytics.domain.MetricValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @description: Public presentation-neutral dashboard contract.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

public record DashboardResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        DashboardGroupBy groupBy,
        String timeZone,
        boolean empty,
        List<MetricResponse> metrics,
        List<MetricResponse> spendingTotals) {

    public static DashboardResponse from(DashboardProjection projection) {
        List<MetricResponse> metrics = projection.metrics().stream().map(MetricResponse::from).toList();
        return new DashboardResponse(
                projection.window().fromUtcInclusive(), projection.window().toUtcExclusive(),
                projection.window().groupBy(), projection.window().timeZone(), projection.empty(), metrics,
                metrics.stream().filter(metric -> "SPENDING_TOTAL".equals(metric.code())).toList());
    }

    public record MetricResponse(
            String code, String label, LocalDate period, BigDecimal value, String unit,
            String currency, Long samples, Long denominator, boolean empty,
            String dimension, String dimensionLabel) {

        private static MetricResponse from(MetricValue value) {
            return new MetricResponse(value.code(), value.label(), value.period(), value.value(), value.unit(),
                    value.currency(), value.samples(), value.denominator(), value.empty(), value.dimension(), value.dimensionLabel());
        }
    }
}
