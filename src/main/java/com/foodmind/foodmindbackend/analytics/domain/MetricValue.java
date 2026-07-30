package com.foodmind.foodmindbackend.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @description: Presentation-neutral metric output with explicit denominator semantics.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

public record MetricValue(
        String code,
        String label,
        LocalDate period,
        BigDecimal value,
        String unit,
        String currency,
        Long samples,
        Long denominator,
        boolean empty,
        String dimension,
        String dimensionLabel) {
}
