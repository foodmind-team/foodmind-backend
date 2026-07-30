package com.foodmind.foodmindbackend.analytics.infrastructure.persistence;

import com.foodmind.foodmindbackend.analytics.application.port.DashboardQuery;
import com.foodmind.foodmindbackend.analytics.domain.AnalyticsWindow;
import com.foodmind.foodmindbackend.analytics.domain.DashboardProjection;
import com.foodmind.foodmindbackend.analytics.domain.MetricDefinition;
import com.foodmind.foodmindbackend.analytics.domain.MetricValue;
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
 * @description: Bounded owner-only reads from the immutable V10 analytics views.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

@Repository
public class DashboardQueryAdapter implements DashboardQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> userTimeZone(UUID actorId) {
        return jdbc.query("""
                        SELECT time_zone FROM app_user
                        WHERE id = :actorId AND status = 'ACTIVE'
                        """,
                new MapSqlParameterSource("actorId", actorId),
                (rs, rowNum) -> rs.getString("time_zone")).stream().findFirst();
    }

    @Override
    public DashboardProjection loadMetrics(UUID actorId, AnalyticsWindow window) {
        MapSqlParameterSource parameters = parameters(actorId, window);
        List<MetricValue> metrics = new ArrayList<>();
        metrics.addAll(consumption(parameters));
        metrics.addAll(spending(parameters));
        metrics.addAll(cuisines(parameters));
        metrics.addAll(repeats(parameters));
        metrics.addAll(recommendations(parameters));
        metrics.addAll(rejectionReasons(parameters));
        metrics.addAll(candidateTypes(parameters));
        return new DashboardProjection(window, metrics);
    }

    private List<MetricValue> consumption(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, record_count, meal_count, drink_count,
                               rated_record_count, mean_rating, would_again_decision_count,
                               would_again_yes_count, would_again_rate
                        FROM analytics_consumption_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local
                        """, parameters, (rs, rowNum) -> {
            LocalDate period = rs.getObject("period_start_local", LocalDate.class);
            long records = rs.getLong("record_count");
            long meals = rs.getLong("meal_count");
            long drinks = rs.getLong("drink_count");
            long rated = rs.getLong("rated_record_count");
            long decided = rs.getLong("would_again_decision_count");
            long yes = rs.getLong("would_again_yes_count");
            return List.of(
                    count(MetricDefinition.FOOD_DRINK_COUNT, period, records, records),
                    count(MetricDefinition.FOOD_COUNT, period, meals, records),
                    count(MetricDefinition.DRINK_COUNT, period, drinks, records),
                    MetricDefinition.MEAN_RATING.map(period, decimal(rs, "mean_rating"), null, rated, rated, null, null),
                    MetricDefinition.WOULD_AGAIN_RATE.map(period, decimal(rs, "would_again_rate"), null, yes, decided, null, null));
        }).stream().flatMap(List::stream).toList();
    }

    private List<MetricValue> spending(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, currency, priced_record_count, total_spend
                        FROM analytics_spending_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local, currency
                        """, parameters, (rs, rowNum) -> MetricDefinition.SPENDING_TOTAL.map(
                rs.getObject("period_start_local", LocalDate.class),
                decimal(rs, "total_spend"),
                rs.getString("currency"),
                rs.getLong("priced_record_count"),
                rs.getLong("priced_record_count"),
                rs.getString("currency"),
                rs.getString("currency")));
    }

    private List<MetricValue> cuisines(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, cuisine_code, cuisine_name, meal_count
                        FROM analytics_cuisine_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local, cuisine_code
                        """, parameters, (rs, rowNum) -> count(
                MetricDefinition.CUISINE_DISTRIBUTION,
                rs.getObject("period_start_local", LocalDate.class),
                rs.getLong("meal_count"),
                rs.getLong("meal_count"),
                rs.getString("cuisine_code"),
                rs.getString("cuisine_name")));
    }

    private List<MetricValue> repeats(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, record_type, item_name, occurrence_count,
                               repeat_count, repeat_frequency
                        FROM analytics_repeat_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local, record_type, item_name
                        """, parameters, (rs, rowNum) -> MetricDefinition.REPEAT_FREQUENCY.map(
                rs.getObject("period_start_local", LocalDate.class),
                decimal(rs, "repeat_frequency"), null,
                rs.getLong("repeat_count"), rs.getLong("occurrence_count"),
                rs.getString("record_type"), rs.getString("item_name")));
    }

    private List<MetricValue> recommendations(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, accepted_count, rejected_count, explicit_decision_count,
                               acceptance_rate, rejection_rate, would_eat_again_decision_count,
                               would_eat_again_yes_count, would_eat_again_rate
                        FROM analytics_recommendation_period_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local
                        """, parameters, (rs, rowNum) -> {
            LocalDate period = rs.getObject("period_start_local", LocalDate.class);
            long accepted = rs.getLong("accepted_count");
            long rejected = rs.getLong("rejected_count");
            long decisions = rs.getLong("explicit_decision_count");
            long wouldAgainDecisions = rs.getLong("would_eat_again_decision_count");
            long wouldAgainYes = rs.getLong("would_eat_again_yes_count");
            return List.of(
                    MetricDefinition.ACCEPTANCE_RATE.map(period, decimal(rs, "acceptance_rate"), null, accepted, decisions, null, null),
                    MetricDefinition.REJECTION_RATE.map(period, decimal(rs, "rejection_rate"), null, rejected, decisions, null, null),
                    MetricDefinition.RECOMMENDATION_WOULD_EAT_AGAIN_RATE.map(period, decimal(rs, "would_eat_again_rate"), null, wouldAgainYes, wouldAgainDecisions, null, null));
        }).stream().flatMap(List::stream).toList();
    }

    private List<MetricValue> rejectionReasons(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, reason_code, rejection_count
                        FROM analytics_rejection_reason_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local, reason_code
                        """, parameters, (rs, rowNum) -> count(
                MetricDefinition.REJECTION_REASON,
                rs.getObject("period_start_local", LocalDate.class),
                rs.getLong("rejection_count"), rs.getLong("rejection_count"),
                rs.getString("reason_code"), rs.getString("reason_code")));
    }

    private List<MetricValue> candidateTypes(MapSqlParameterSource parameters) {
        return jdbc.query("""
                        SELECT period_start_local, candidate_type, accepted_count
                        FROM analytics_candidate_type_selection_v1
                        WHERE user_id = :actorId
                          AND aggregation_time_zone = :timeZone
                          AND period_grain = :groupBy
                          AND period_start_local >= :fromLocal
                          AND period_start_local < :toLocal
                        ORDER BY period_start_local, candidate_type
                        """, parameters, (rs, rowNum) -> count(
                MetricDefinition.SELECTED_CANDIDATE_TYPE,
                rs.getObject("period_start_local", LocalDate.class),
                rs.getLong("accepted_count"), rs.getLong("accepted_count"),
                rs.getString("candidate_type"), rs.getString("candidate_type")));
    }

    private MapSqlParameterSource parameters(UUID actorId, AnalyticsWindow window) {
        return new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("fromUtcInclusive", window.fromUtcInclusive())
                .addValue("toUtcExclusive", window.toUtcExclusive())
                .addValue("fromLocal", window.fromLocalInclusive())
                .addValue("toLocal", window.toLocalExclusive())
                .addValue("groupBy", window.groupBy().name())
                .addValue("timeZone", window.timeZone());
    }

    private MetricValue count(MetricDefinition definition, LocalDate period, long value, long denominator) {
        return count(definition, period, value, denominator, null, null);
    }

    private MetricValue count(
            MetricDefinition definition, LocalDate period, long value, long denominator, String dimension, String label) {
        return definition.map(period, BigDecimal.valueOf(value), null, value, denominator, dimension, label);
    }

    private BigDecimal decimal(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        return resultSet.getBigDecimal(column);
    }
}
