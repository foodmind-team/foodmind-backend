package com.foodmind.foodmindbackend.analytics.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @description: One validated UTC range and its local calendar representation.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

public record AnalyticsWindow(
        OffsetDateTime fromUtcInclusive,
        OffsetDateTime toUtcExclusive,
        LocalDate fromLocalInclusive,
        LocalDate toLocalExclusive,
        DashboardGroupBy groupBy,
        String timeZone) {
}
