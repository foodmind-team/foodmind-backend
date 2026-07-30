package com.foodmind.foodmindbackend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * @description: Guards the shared Web and Android analytics fixture invariants.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 05:00 pm
 */

class AnalyticsFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixtureDeclaresDistinctCurrencyAndUndefinedDenominatorRules() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/analytics/dashboard-metrics.json")) {
            JsonNode fixture = objectMapper.readTree(input);

            assertThat(fixture.at("/expectations/currencyTotals")).hasSize(2);
            assertThat(fixture.at("/expectations/currencyTotals/0/currency").asText()).isNotEqualTo(
                    fixture.at("/expectations/currencyTotals/1/currency").asText());
            assertThat(fixture.at("/expectations/undefinedDenominator/empty").asBoolean()).isTrue();
            assertThat(fixture.at("/expectations/undefinedDenominator/value").isNull()).isTrue();
        }
    }
}
