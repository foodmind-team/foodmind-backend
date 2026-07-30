package com.foodmind.foodmindbackend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.analytics.domain.MetricDefinition;
import com.foodmind.foodmindbackend.analytics.domain.MetricValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * @description: Verifies metric mapping preserves currency and denominator metadata.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:45 pm
 */

class MetricDefinitionTest {

    @Test
    void mapsCurrencyWithoutCrossCurrencyConversion() {
        MetricValue metric = MetricDefinition.SPENDING_TOTAL.map(
                LocalDate.of(2026, 7, 27), new BigDecimal("12.50"), "SGD", 2L, 2L, "SGD", "SGD");

        assertThat(metric.code()).isEqualTo("SPENDING_TOTAL");
        assertThat(metric.currency()).isEqualTo("SGD");
        assertThat(metric.unit()).isEqualTo("MONEY");
        assertThat(metric.denominator()).isEqualTo(2L);
        assertThat(metric.empty()).isFalse();
    }

    @Test
    void representsUndefinedDenominatorsAsExplicitEmptyValues() {
        MetricValue metric = MetricDefinition.ACCEPTANCE_RATE.map(
                LocalDate.of(2026, 7, 27), null, null, 0L, 0L, null, null);

        assertThat(metric.empty()).isTrue();
        assertThat(metric.value()).isNull();
        assertThat(metric.denominator()).isZero();
    }
}
