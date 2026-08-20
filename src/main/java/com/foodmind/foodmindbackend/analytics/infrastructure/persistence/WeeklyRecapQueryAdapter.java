package com.foodmind.foodmindbackend.analytics.infrastructure.persistence;

import com.foodmind.foodmindbackend.analytics.application.port.WeeklyRecapQuery;
import com.foodmind.foodmindbackend.analytics.domain.MetricDefinition;
import com.foodmind.foodmindbackend.analytics.domain.MetricValue;
import com.foodmind.foodmindbackend.analytics.domain.WeeklyRecapProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description: Bounded V10 weekly recap and currency-grouped spending reads.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:35 pm
 */

@Repository
public class WeeklyRecapQueryAdapter implements WeeklyRecapQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public WeeklyRecapQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> userTimeZone(UUID actorId) {
        return jdbc.query("""
                        SELECT time_zone FROM app_user
                        WHERE id = :actorId AND status = 'ACTIVE'
                        """, new MapSqlParameterSource("actorId", actorId),
                (rs, rowNum) -> rs.getString("time_zone")).stream().findFirst();
    }

    @Override
    public WeeklyRecapProjection loadWeek(UUID actorId, LocalDate weekStart, String timeZone) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("weekStart", weekStart)
                .addValue("timeZone", timeZone);
        List<MetricValue> metrics = new ArrayList<>(jdbc.query("""
                        SELECT meal_count, drink_count, mean_rating,
                               record_would_again_decision_count, record_would_again_rate,
                               accepted_count, rejected_count, acceptance_rate,
                               recommendation_would_eat_again_rate
                        FROM analytics_weekly_recap_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND week_start_local = :weekStart
                        """, parameters, (rs, rowNum) -> List.of(
                count(MetricDefinition.FOOD_COUNT, weekStart, rs.getLong("meal_count"), rs.getLong("meal_count")),
                count(MetricDefinition.DRINK_COUNT, weekStart, rs.getLong("drink_count"), rs.getLong("drink_count")),
                MetricDefinition.MEAN_RATING.map(weekStart, rs.getBigDecimal("mean_rating"), null, null, null, null, null),
                MetricDefinition.WOULD_AGAIN_RATE.map(weekStart, rs.getBigDecimal("record_would_again_rate"), null, null,
                        rs.getLong("record_would_again_decision_count"), null, null),
                MetricDefinition.ACCEPTANCE_RATE.map(weekStart, rs.getBigDecimal("acceptance_rate"), null,
                        rs.getLong("accepted_count"), rs.getLong("accepted_count") + rs.getLong("rejected_count"), null, null),
                MetricDefinition.RECOMMENDATION_WOULD_EAT_AGAIN_RATE.map(weekStart,
                        rs.getBigDecimal("recommendation_would_eat_again_rate"), null, null, null, null, null)))
                .stream().flatMap(List::stream).toList());
        metrics.addAll(jdbc.query("""
                        SELECT cuisine_code, cuisine_name, meal_count
                        FROM analytics_cuisine_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = 'WEEK'
                          AND period_start_local = :weekStart
                        ORDER BY cuisine_code
                        """, parameters, (rs, rowNum) -> count(
                MetricDefinition.CUISINE_DISTRIBUTION,
                weekStart,
                rs.getLong("meal_count"),
                rs.getLong("meal_count"),
                rs.getString("cuisine_code"),
                rs.getString("cuisine_name"))));
        metrics.addAll(jdbc.query("""
                        SELECT currency, priced_record_count, total_spend
                        FROM analytics_spending_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = 'WEEK'
                          AND period_start_local = :weekStart
                        ORDER BY currency
                        """, parameters, (rs, rowNum) -> MetricDefinition.SPENDING_TOTAL.map(
                weekStart, rs.getBigDecimal("total_spend"), rs.getString("currency"),
                rs.getLong("priced_record_count"), rs.getLong("priced_record_count"),
                rs.getString("currency"), rs.getString("currency"))));
        return new WeeklyRecapProjection(weekStart, timeZone, metrics);
    }

    private MetricValue count(MetricDefinition definition, LocalDate period, long value, long denominator) {
        return count(definition, period, value, denominator, null, null);
    }

    private MetricValue count(
            MetricDefinition definition,
            LocalDate period,
            long value,
            long denominator,
            String dimension,
            String dimensionLabel) {
        return definition.map(period, BigDecimal.valueOf(value), null, value, denominator, dimension, dimensionLabel);
    }
}
