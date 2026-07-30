package com.foodmind.foodmindbackend.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @description: Stable server-owned dashboard metric definitions and mapping rules.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

public enum MetricDefinition {
    FOOD_DRINK_COUNT("Food and drink count", "COUNT"),
    FOOD_COUNT("Food count", "COUNT"),
    DRINK_COUNT("Drink count", "COUNT"),
    MEAN_RATING("Mean rating", "RATING"),
    SPENDING_TOTAL("Spending total", "MONEY"),
    CUISINE_DISTRIBUTION("Cuisine distribution", "COUNT"),
    REPEAT_FREQUENCY("Repeat frequency", "RATE"),
    ACCEPTANCE_RATE("Recommendation acceptance rate", "RATE"),
    REJECTION_RATE("Recommendation rejection rate", "RATE"),
    REJECTION_REASON("Recommendation rejection reason", "COUNT"),
    WOULD_AGAIN_RATE("Would eat or buy again rate", "RATE"),
    RECOMMENDATION_WOULD_EAT_AGAIN_RATE("Recommendation would eat again rate", "RATE"),
    SELECTED_CANDIDATE_TYPE("Selected candidate type", "COUNT");

    private final String label;
    private final String unit;

    MetricDefinition(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public MetricValue map(
            LocalDate period,
            BigDecimal value,
            String currency,
            Long samples,
            Long denominator,
            String dimension,
            String dimensionLabel) {
        return new MetricValue(
                name(),
                label,
                period,
                value,
                currency == null ? unit : "MONEY",
                currency,
                samples,
                denominator,
                value == null,
                dimension,
                dimensionLabel);
    }
}
