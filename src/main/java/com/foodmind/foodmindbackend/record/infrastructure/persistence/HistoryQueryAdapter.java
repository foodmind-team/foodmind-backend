package com.foodmind.foodmindbackend.record.infrastructure.persistence;

import com.foodmind.foodmindbackend.record.application.port.HistoryQuery;
import com.foodmind.foodmindbackend.record.domain.HistoryBucket;
import com.foodmind.foodmindbackend.record.domain.HistoryCursor;
import com.foodmind.foodmindbackend.record.domain.HistoryEntry;
import com.foodmind.foodmindbackend.record.domain.HistoryFilter;
import com.foodmind.foodmindbackend.record.domain.HistorySourceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

@Repository
public class HistoryQueryAdapter implements HistoryQuery {

    private static final String FOOD_PERSONAL_HISTORY_PREDICATE = """
            fr.deleted_at IS NULL
            AND fr.occurred_at >= :fromUtcInclusive
            AND fr.occurred_at < :toUtcExclusive
            AND fr.owner_user_id = :actorUserId
            """;

    private static final String DRINK_PERSONAL_HISTORY_PREDICATE = """
            dr.deleted_at IS NULL
            AND dr.occurred_at >= :fromUtcInclusive
            AND dr.occurred_at < :toUtcExclusive
            AND dr.owner_user_id = :actorUserId
            """;

    private static final String HISTORY_UNION = """
            SELECT 'FOOD'::text AS source_type,
                   fr.id AS source_id,
                   fr.occurred_at,
                   fr.meal_name_snapshot AS title,
                   fr.place_name_snapshot AS context,
                   fr.group_id,
                   fr.cuisine_id,
                   fr.place_id,
                   fr.rating,
                   fr.would_eat_again AS repeat_intent
            FROM food_record fr
            WHERE %s
            UNION ALL
            SELECT 'DRINK'::text AS source_type,
                   dr.id AS source_id,
                   dr.occurred_at,
                   dr.drink_name AS title,
                   dr.shop_name_snapshot AS context,
                   dr.group_id,
                   NULL::uuid AS cuisine_id,
                   dr.place_id,
                   dr.rating,
                   dr.would_buy_again AS repeat_intent
            FROM drink_record dr
            WHERE %s
            """.formatted(FOOD_PERSONAL_HISTORY_PREDICATE, DRINK_PERSONAL_HISTORY_PREDICATE);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HistoryQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> userTimeZone(UUID actorUserId) {
        return jdbcTemplate.query("""
                        SELECT time_zone
                        FROM app_user
                        WHERE id = :actorUserId
                          AND status = 'ACTIVE'
                        """,
                new MapSqlParameterSource("actorUserId", actorUserId),
                (rs, rowNum) -> rs.getString("time_zone"))
                .stream()
                .findFirst();
    }

    @Override
    public List<HistoryEntry> findAuthorisedHistory(UUID actorUserId, HistoryFilter filter) {
        MapSqlParameterSource params = parameters(actorUserId, filter);
        return jdbcTemplate.query("""
                        SELECT h.source_type,
                               h.source_id,
                               h.occurred_at,
                               %s AS local_bucket_start,
                               h.title,
                               h.context,
                               h.group_id,
                               h.cuisine_id,
                               h.place_id,
                               h.rating,
                               h.repeat_intent
                        FROM (%s) h
                        WHERE %s
                          AND %s
                        ORDER BY h.occurred_at DESC, h.source_type ASC, h.source_id DESC
                        LIMIT :limit
                        """.formatted(bucketExpression(), HISTORY_UNION, filterClause(), cursorClause()),
                params,
                (rs, rowNum) -> new HistoryEntry(
                        HistorySourceType.valueOf(rs.getString("source_type")),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("local_bucket_start", LocalDate.class),
                        rs.getString("title"),
                        rs.getString("context"),
                        rs.getObject("group_id", UUID.class),
                        rs.getObject("cuisine_id", UUID.class),
                        rs.getObject("place_id", UUID.class),
                        rs.getBigDecimal("rating"),
                        nullableBoolean(rs.getBoolean("repeat_intent"), rs.wasNull())));
    }

    @Override
    public List<HistoryBucket> findAuthorisedBuckets(UUID actorUserId, HistoryFilter filter) {
        MapSqlParameterSource params = parameters(actorUserId, filter);
        return jdbcTemplate.query("""
                        SELECT %s AS local_bucket_start,
                               count(*) AS total_count,
                               count(*) FILTER (WHERE h.source_type = 'FOOD') AS food_count,
                               count(*) FILTER (WHERE h.source_type = 'DRINK') AS drink_count
                        FROM (%s) h
                        WHERE %s
                        GROUP BY local_bucket_start
                        ORDER BY local_bucket_start ASC
                        """.formatted(bucketExpression(), HISTORY_UNION, filterClause()),
                params,
                (rs, rowNum) -> new HistoryBucket(
                        rs.getObject("local_bucket_start", LocalDate.class),
                        rs.getLong("total_count"),
                        rs.getLong("food_count"),
                        rs.getLong("drink_count")));
    }

    private MapSqlParameterSource parameters(UUID actorUserId, HistoryFilter filter) {
        HistoryCursor cursor = filter.after();
        return new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("fromUtcInclusive", filter.fromUtcInclusive())
                .addValue("toUtcExclusive", filter.toUtcExclusive())
                .addValue("period", filter.period().name())
                .addValue("types", filter.types().stream().map(Enum::name).toList())
                .addValue("timeZone", filter.timeZone())
                .addValue("groupId", filter.groupId())
                .addValue("cuisineId", filter.cuisineId())
                .addValue("placeId", filter.placeId())
                .addValue("cursorOccurredAt", cursor == null ? null : cursor.occurredAt())
                .addValue("cursorSourceType", cursor == null ? null : cursor.sourceType().name())
                .addValue("cursorSourceId", cursor == null ? null : cursor.sourceId().toString())
                .addValue("limit", filter.size());
    }

    private String bucketExpression() {
        return """
                CASE :period
                    WHEN 'DAY' THEN date_trunc('day', h.occurred_at AT TIME ZONE :timeZone)::date
                    WHEN 'WEEK' THEN date_trunc('week', h.occurred_at AT TIME ZONE :timeZone)::date
                    ELSE date_trunc('month', h.occurred_at AT TIME ZONE :timeZone)::date
                END
                """;
    }

    private String filterClause() {
        return """
                h.source_type IN (:types)
                AND (CAST(:groupId AS uuid) IS NULL OR h.group_id = :groupId)
                AND (CAST(:cuisineId AS uuid) IS NULL OR h.cuisine_id = :cuisineId)
                AND (CAST(:placeId AS uuid) IS NULL OR h.place_id = :placeId)
                """;
    }

    private String cursorClause() {
        return """
                (
                    CAST(:cursorOccurredAt AS timestamptz) IS NULL
                    OR h.occurred_at < :cursorOccurredAt
                    OR (
                        h.occurred_at = :cursorOccurredAt
                        AND h.source_type > CAST(:cursorSourceType AS text)
                    )
                    OR (
                        h.occurred_at = :cursorOccurredAt
                        AND h.source_type = CAST(:cursorSourceType AS text)
                        AND h.source_id::text < CAST(:cursorSourceId AS text)
                    )
                )
                """;
    }

    private Boolean nullableBoolean(boolean value, boolean wasNull) {
        return wasNull ? null : value;
    }
}
