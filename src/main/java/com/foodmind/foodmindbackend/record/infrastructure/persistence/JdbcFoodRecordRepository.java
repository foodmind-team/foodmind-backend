package com.foodmind.foodmindbackend.record.infrastructure.persistence;

import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.record.domain.FoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordFilter;
import com.foodmind.foodmindbackend.record.domain.FoodRecordPage;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import com.foodmind.foodmindbackend.record.domain.MealNoteView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

@Repository
public class JdbcFoodRecordRepository implements FoodRecordQuery {

    private static final String AUTHORISED_PREDICATE = """
            fr.deleted_at IS NULL
            AND (
                fr.owner_user_id = :actorUserId
                OR (
                    fr.visibility = 'GROUP'
                    AND EXISTS (
                        SELECT 1
                        FROM group_membership gm
                        JOIN trusted_group tg ON tg.id = gm.group_id
                        WHERE gm.group_id = fr.group_id
                          AND gm.user_id = :actorUserId
                          AND gm.status = 'ACTIVE'
                          AND tg.status = 'ACTIVE'
                    )
                )
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcFoodRecordRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FoodRecord create(FoodRecord record) {
        jdbcTemplate.update("""
                        INSERT INTO food_record (
                            id, owner_user_id, meal_id, meal_name_snapshot, place_id, place_name_snapshot,
                            cuisine_id, occurred_at, price, currency, rating, comment, would_eat_again,
                            visibility, group_id, media_asset_id, version
                        )
                        VALUES (
                            :id, :ownerUserId, :mealId, :mealNameSnapshot, :placeId, :placeNameSnapshot,
                            :cuisineId, :occurredAt, :price, :currency, :rating, :comment, :wouldEatAgain,
                            :visibility, :groupId, :mediaAssetId, :version
                        )
                        """,
                recordParameters(record));
        return findOwnerRecord(record.ownerUserId(), record.id()).orElseThrow();
    }

    @Override
    public Optional<FoodRecord> findAuthorised(UUID actorUserId, UUID id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("id", id);
        return jdbcTemplate.query("""
                        SELECT fr.id, fr.owner_user_id, fr.meal_id, fr.meal_name_snapshot,
                               fr.place_id, fr.place_name_snapshot, fr.cuisine_id,
                               c.code AS cuisine_code, c.name AS cuisine_name,
                               fr.occurred_at, fr.price, fr.currency, fr.rating, fr.comment,
                               fr.would_eat_again, fr.visibility, fr.group_id, fr.media_asset_id,
                               fr.created_at, fr.updated_at, fr.version
                        FROM food_record fr
                        LEFT JOIN cuisine c ON c.id = fr.cuisine_id
                        WHERE fr.id = :id
                          AND %s
                        """.formatted(AUTHORISED_PREDICATE),
                params,
                foodRecordRowMapper(true))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<FoodRecord> findOwnerRecord(UUID ownerUserId, UUID id) {
        return jdbcTemplate.query("""
                        SELECT fr.id, fr.owner_user_id, fr.meal_id, fr.meal_name_snapshot,
                               fr.place_id, fr.place_name_snapshot, fr.cuisine_id,
                               c.code AS cuisine_code, c.name AS cuisine_name,
                               fr.occurred_at, fr.price, fr.currency, fr.rating, fr.comment,
                               fr.would_eat_again, fr.visibility, fr.group_id, fr.media_asset_id,
                               fr.created_at, fr.updated_at, fr.version
                        FROM food_record fr
                        LEFT JOIN cuisine c ON c.id = fr.cuisine_id
                        WHERE fr.id = :id
                          AND fr.owner_user_id = :ownerUserId
                          AND fr.deleted_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("ownerUserId", ownerUserId)
                        .addValue("id", id),
                foodRecordRowMapper(true))
                .stream()
                .findFirst();
    }

    @Override
    public FoodRecordPage listAuthorised(UUID actorUserId, FoodRecordFilter filter) {
        MapSqlParameterSource params = filterParameters(actorUserId, filter);
        String where = listWhereClause(filter);
        long totalItems = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM food_record fr WHERE " + where,
                params,
                Long.class);
        List<FoodRecord> items = jdbcTemplate.query("""
                        SELECT fr.id, fr.owner_user_id, fr.meal_id, fr.meal_name_snapshot,
                               fr.place_id, fr.place_name_snapshot, fr.cuisine_id,
                               c.code AS cuisine_code, c.name AS cuisine_name,
                               fr.occurred_at, fr.price, fr.currency, fr.rating,
                               NULL::text AS comment, fr.would_eat_again, fr.visibility, fr.group_id,
                               NULL::uuid AS media_asset_id, fr.created_at, fr.updated_at, fr.version
                        FROM food_record fr
                        LEFT JOIN cuisine c ON c.id = fr.cuisine_id
                        WHERE %s
                        ORDER BY %s, fr.id ASC
                        LIMIT :limit OFFSET :offset
                        """.formatted(where, orderBy(filter.sort())),
                params,
                foodRecordRowMapper(false));
        return new FoodRecordPage(items, totalItems == 0 ? 0 : totalItems);
    }

    @Override
    public FoodRecord update(FoodRecord record) {
        jdbcTemplate.update("""
                        UPDATE food_record
                        SET meal_id = :mealId,
                            meal_name_snapshot = :mealNameSnapshot,
                            place_id = :placeId,
                            place_name_snapshot = :placeNameSnapshot,
                            cuisine_id = :cuisineId,
                            occurred_at = :occurredAt,
                            price = :price,
                            currency = :currency,
                            rating = :rating,
                            comment = :comment,
                            would_eat_again = :wouldEatAgain,
                            visibility = :visibility,
                            group_id = :groupId,
                            media_asset_id = :mediaAssetId,
                            version = :version
                        WHERE id = :id
                          AND owner_user_id = :ownerUserId
                          AND deleted_at IS NULL
                        """,
                recordParameters(record));
        return findOwnerRecord(record.ownerUserId(), record.id()).orElseThrow();
    }

    @Override
    public boolean softDelete(UUID ownerUserId, UUID id) {
        int rows = jdbcTemplate.update("""
                        UPDATE food_record
                        SET deleted_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id
                          AND owner_user_id = :ownerUserId
                          AND deleted_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("ownerUserId", ownerUserId)
                        .addValue("id", id));
        return rows > 0;
    }

    @Override
    public boolean mealExists(UUID mealId) {
        return exists("SELECT count(*) FROM meal WHERE id = :id AND curation_status = 'ACTIVE'", mealId);
    }

    @Override
    public boolean placeExists(UUID placeId) {
        return exists("SELECT count(*) FROM place WHERE id = :id AND curation_status = 'ACTIVE'", placeId);
    }

    @Override
    public boolean cuisineExists(UUID cuisineId) {
        return exists("SELECT count(*) FROM cuisine WHERE id = :id", cuisineId);
    }

    @Override
    public boolean readyMediaExistsForOwner(UUID ownerUserId, UUID mediaAssetId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM media_asset
                        WHERE id = :id
                          AND owner_user_id = :ownerUserId
                          AND status = 'READY'
                          AND deleted_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("id", mediaAssetId)
                        .addValue("ownerUserId", ownerUserId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<MealNoteView> mealNotesForUser(UUID actorUserId, int limit) {
        return jdbcTemplate.query("""
                        SELECT fr.id, fr.meal_id, fr.meal_name_snapshot, fr.place_id, fr.place_name_snapshot,
                               fr.cuisine_id, c.code AS cuisine_code, fr.occurred_at, fr.rating,
                               fr.would_eat_again, fr.visibility, fr.group_id
                        FROM food_record fr
                        LEFT JOIN cuisine c ON c.id = fr.cuisine_id
                        WHERE %s
                        ORDER BY fr.occurred_at DESC, fr.id ASC
                        LIMIT :limit
                        """.formatted(AUTHORISED_PREDICATE),
                new MapSqlParameterSource()
                        .addValue("actorUserId", actorUserId)
                        .addValue("limit", Math.max(1, Math.min(limit, 100))),
                this::mealNoteRow);
    }

    private boolean exists(String sql, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("id", id),
                Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource recordParameters(FoodRecord record) {
        return new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("ownerUserId", record.ownerUserId())
                .addValue("mealId", record.mealId())
                .addValue("mealNameSnapshot", record.mealNameSnapshot())
                .addValue("placeId", record.placeId())
                .addValue("placeNameSnapshot", record.placeNameSnapshot())
                .addValue("cuisineId", record.cuisineId())
                .addValue("occurredAt", record.occurredAt())
                .addValue("price", record.price())
                .addValue("currency", record.currency())
                .addValue("rating", record.rating())
                .addValue("comment", record.comment())
                .addValue("wouldEatAgain", record.wouldEatAgain())
                .addValue("visibility", record.visibility().name())
                .addValue("groupId", record.groupId())
                .addValue("mediaAssetId", record.mediaAssetId())
                .addValue("version", record.version());
    }

    private MapSqlParameterSource filterParameters(UUID actorUserId, FoodRecordFilter filter) {
        return new MapSqlParameterSource(new LinkedHashMap<>(Map.of(
                "actorUserId", actorUserId,
                "limit", filter.size(),
                "offset", Math.multiplyExact(filter.page(), filter.size()))))
                .addValue("from", filter.from())
                .addValue("to", filter.to())
                .addValue("cuisineId", filter.cuisineId())
                .addValue("mealId", filter.mealId())
                .addValue("placeId", filter.placeId())
                .addValue("visibility", filter.visibility() == null ? null : filter.visibility().name())
                .addValue("groupId", filter.groupId())
                .addValue("minRating", filter.minRating())
                .addValue("maxRating", filter.maxRating());
    }

    private String listWhereClause(FoodRecordFilter filter) {
        StringBuilder where = new StringBuilder(AUTHORISED_PREDICATE);
        if (filter.from() != null) {
            where.append(" AND fr.occurred_at >= :from");
        }
        if (filter.to() != null) {
            where.append(" AND fr.occurred_at <= :to");
        }
        if (filter.cuisineId() != null) {
            where.append(" AND fr.cuisine_id = :cuisineId");
        }
        if (filter.mealId() != null) {
            where.append(" AND fr.meal_id = :mealId");
        }
        if (filter.placeId() != null) {
            where.append(" AND fr.place_id = :placeId");
        }
        if (filter.visibility() != null) {
            where.append(" AND fr.visibility = :visibility");
        }
        if (filter.groupId() != null) {
            where.append(" AND fr.group_id = :groupId");
        }
        if (filter.minRating() != null) {
            where.append(" AND fr.rating >= :minRating");
        }
        if (filter.maxRating() != null) {
            where.append(" AND fr.rating <= :maxRating");
        }
        return where.toString();
    }

    private String orderBy(String sort) {
        String normalized = sort == null ? "occurredAt,desc" : sort.trim();
        return switch (normalized) {
            case "occurredAt,asc" -> "fr.occurred_at ASC";
            case "createdAt,asc" -> "fr.created_at ASC";
            case "createdAt,desc" -> "fr.created_at DESC";
            case "rating,asc" -> "fr.rating ASC NULLS LAST";
            case "rating,desc" -> "fr.rating DESC NULLS LAST";
            default -> "fr.occurred_at DESC";
        };
    }

    private RowMapper<FoodRecord> foodRecordRowMapper(boolean includeCommentsAndMedia) {
        return (rs, rowNum) -> new FoodRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_user_id", UUID.class),
                rs.getObject("meal_id", UUID.class),
                rs.getString("meal_name_snapshot"),
                rs.getObject("place_id", UUID.class),
                rs.getString("place_name_snapshot"),
                rs.getObject("cuisine_id", UUID.class),
                rs.getString("cuisine_code"),
                rs.getString("cuisine_name"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getBigDecimal("price"),
                rs.getString("currency") == null ? null : rs.getString("currency").trim(),
                rs.getBigDecimal("rating"),
                includeCommentsAndMedia ? rs.getString("comment") : null,
                nullableBoolean(rs, "would_eat_again"),
                FoodRecordVisibility.valueOf(rs.getString("visibility")),
                rs.getObject("group_id", UUID.class),
                includeCommentsAndMedia ? rs.getObject("media_asset_id", UUID.class) : null,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getLong("version"));
    }

    private MealNoteView mealNoteRow(ResultSet rs, int rowNum) throws SQLException {
        return new MealNoteView(
                rs.getObject("id", UUID.class),
                rs.getObject("meal_id", UUID.class),
                rs.getString("meal_name_snapshot"),
                rs.getObject("place_id", UUID.class),
                rs.getString("place_name_snapshot"),
                rs.getObject("cuisine_id", UUID.class),
                rs.getString("cuisine_code"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getBigDecimal("rating"),
                nullableBoolean(rs, "would_eat_again"),
                FoodRecordVisibility.valueOf(rs.getString("visibility")),
                rs.getObject("group_id", UUID.class));
    }

    private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
