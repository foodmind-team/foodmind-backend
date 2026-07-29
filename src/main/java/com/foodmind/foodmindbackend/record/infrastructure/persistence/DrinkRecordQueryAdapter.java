package com.foodmind.foodmindbackend.record.infrastructure.persistence;

import com.foodmind.foodmindbackend.record.application.port.DrinkRecordQuery;
import com.foodmind.foodmindbackend.record.domain.DrinkRecord;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordFilter;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordPage;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
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
 * @date: 30/07/2026 01:11 am
 */

@Repository
public class DrinkRecordQueryAdapter implements DrinkRecordQuery {

    private static final String AUTHORISED_PREDICATE = """
            dr.deleted_at IS NULL
            AND (
                dr.owner_user_id = :actorUserId
                OR (
                    dr.visibility = 'GROUP'
                    AND EXISTS (
                        SELECT 1
                        FROM group_membership gm
                        JOIN trusted_group tg ON tg.id = gm.group_id
                        WHERE gm.group_id = dr.group_id
                          AND gm.user_id = :actorUserId
                          AND gm.status = 'ACTIVE'
                          AND tg.status = 'ACTIVE'
                    )
                )
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DrinkRecordQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DrinkRecord create(DrinkRecord record) {
        jdbcTemplate.update("""
                        INSERT INTO drink_record (
                            id, owner_user_id, drink_name, place_id, shop_name_snapshot,
                            occurred_at, price, currency, rating, comment, sweetness_level,
                            ice_level, would_buy_again, visibility, group_id, media_asset_id, version
                        )
                        VALUES (
                            :id, :ownerUserId, :drinkName, :placeId, :shopNameSnapshot,
                            :occurredAt, :price, :currency, :rating, :comment, :sweetnessLevel,
                            :iceLevel, :wouldBuyAgain, :visibility, :groupId, :mediaAssetId, :version
                        )
                        """,
                recordParameters(record));
        return findOwnerRecord(record.ownerUserId(), record.id()).orElseThrow();
    }

    @Override
    public Optional<DrinkRecord> findVisibleById(UUID actorUserId, UUID id) {
        return jdbcTemplate.query("""
                        SELECT dr.id, dr.owner_user_id, dr.drink_name, dr.place_id,
                               dr.shop_name_snapshot, dr.occurred_at, dr.price, dr.currency,
                               dr.rating, dr.comment, dr.sweetness_level, dr.ice_level,
                               dr.would_buy_again, dr.visibility, dr.group_id, dr.media_asset_id,
                               dr.created_at, dr.updated_at, dr.version
                        FROM drink_record dr
                        WHERE dr.id = :id
                          AND %s
                        """.formatted(AUTHORISED_PREDICATE),
                new MapSqlParameterSource()
                        .addValue("actorUserId", actorUserId)
                        .addValue("id", id),
                drinkRecordRowMapper(true))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<DrinkRecord> findOwnerRecord(UUID ownerUserId, UUID id) {
        return jdbcTemplate.query("""
                        SELECT dr.id, dr.owner_user_id, dr.drink_name, dr.place_id,
                               dr.shop_name_snapshot, dr.occurred_at, dr.price, dr.currency,
                               dr.rating, dr.comment, dr.sweetness_level, dr.ice_level,
                               dr.would_buy_again, dr.visibility, dr.group_id, dr.media_asset_id,
                               dr.created_at, dr.updated_at, dr.version
                        FROM drink_record dr
                        WHERE dr.id = :id
                          AND dr.owner_user_id = :ownerUserId
                          AND dr.deleted_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("ownerUserId", ownerUserId)
                        .addValue("id", id),
                drinkRecordRowMapper(true))
                .stream()
                .findFirst();
    }

    @Override
    public DrinkRecordPage listAuthorised(UUID actorUserId, DrinkRecordFilter filter) {
        MapSqlParameterSource params = filterParameters(actorUserId, filter);
        String where = listWhereClause(filter);
        long totalItems = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM drink_record dr WHERE " + where,
                params,
                Long.class);
        List<DrinkRecord> items = jdbcTemplate.query("""
                        SELECT dr.id, dr.owner_user_id, dr.drink_name, dr.place_id,
                               dr.shop_name_snapshot, dr.occurred_at, dr.price, dr.currency,
                               dr.rating, NULL::text AS comment, dr.sweetness_level, dr.ice_level,
                               dr.would_buy_again, dr.visibility, dr.group_id,
                               NULL::uuid AS media_asset_id, dr.created_at, dr.updated_at, dr.version
                        FROM drink_record dr
                        WHERE %s
                        ORDER BY %s, dr.id ASC
                        LIMIT :limit OFFSET :offset
                        """.formatted(where, orderBy(filter.sort())),
                params,
                drinkRecordRowMapper(false));
        return new DrinkRecordPage(items, totalItems == 0 ? 0 : totalItems);
    }

    @Override
    public DrinkRecord update(DrinkRecord record) {
        jdbcTemplate.update("""
                        UPDATE drink_record
                        SET drink_name = :drinkName,
                            place_id = :placeId,
                            shop_name_snapshot = :shopNameSnapshot,
                            occurred_at = :occurredAt,
                            price = :price,
                            currency = :currency,
                            rating = :rating,
                            comment = :comment,
                            sweetness_level = :sweetnessLevel,
                            ice_level = :iceLevel,
                            would_buy_again = :wouldBuyAgain,
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
                        UPDATE drink_record
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
    public boolean placeExists(UUID placeId) {
        return exists("SELECT count(*) FROM place WHERE id = :id AND curation_status = 'ACTIVE'", placeId);
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

    private boolean exists(String sql, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("id", id),
                Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource recordParameters(DrinkRecord record) {
        return new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("ownerUserId", record.ownerUserId())
                .addValue("drinkName", record.drinkName())
                .addValue("placeId", record.placeId())
                .addValue("shopNameSnapshot", record.shopNameSnapshot())
                .addValue("occurredAt", record.occurredAt())
                .addValue("price", record.price())
                .addValue("currency", record.currency())
                .addValue("rating", record.rating())
                .addValue("comment", record.comment())
                .addValue("sweetnessLevel", record.sweetnessLevel())
                .addValue("iceLevel", record.iceLevel())
                .addValue("wouldBuyAgain", record.wouldBuyAgain())
                .addValue("visibility", record.visibility().name())
                .addValue("groupId", record.groupId())
                .addValue("mediaAssetId", record.mediaAssetId())
                .addValue("version", record.version());
    }

    private MapSqlParameterSource filterParameters(UUID actorUserId, DrinkRecordFilter filter) {
        return new MapSqlParameterSource(new LinkedHashMap<>(Map.of(
                "actorUserId", actorUserId,
                "limit", filter.size(),
                "offset", Math.multiplyExact(filter.page(), filter.size()))))
                .addValue("from", filter.from())
                .addValue("to", filter.to())
                .addValue("placeId", filter.placeId())
                .addValue("visibility", filter.visibility() == null ? null : filter.visibility().name())
                .addValue("groupId", filter.groupId())
                .addValue("minRating", filter.minRating())
                .addValue("maxRating", filter.maxRating());
    }

    private String listWhereClause(DrinkRecordFilter filter) {
        StringBuilder where = new StringBuilder(AUTHORISED_PREDICATE);
        if (filter.from() != null) {
            where.append(" AND dr.occurred_at >= :from");
        }
        if (filter.to() != null) {
            where.append(" AND dr.occurred_at <= :to");
        }
        if (filter.placeId() != null) {
            where.append(" AND dr.place_id = :placeId");
        }
        if (filter.visibility() != null) {
            where.append(" AND dr.visibility = :visibility");
        }
        if (filter.groupId() != null) {
            where.append(" AND dr.group_id = :groupId");
        }
        if (filter.minRating() != null) {
            where.append(" AND dr.rating >= :minRating");
        }
        if (filter.maxRating() != null) {
            where.append(" AND dr.rating <= :maxRating");
        }
        return where.toString();
    }

    private String orderBy(String sort) {
        String normalized = sort == null ? "occurredAt,desc" : sort.trim();
        return switch (normalized) {
            case "occurredAt,asc" -> "dr.occurred_at ASC";
            case "createdAt,asc" -> "dr.created_at ASC";
            case "createdAt,desc" -> "dr.created_at DESC";
            case "rating,asc" -> "dr.rating ASC NULLS LAST";
            case "rating,desc" -> "dr.rating DESC NULLS LAST";
            default -> "dr.occurred_at DESC";
        };
    }

    private RowMapper<DrinkRecord> drinkRecordRowMapper(boolean includeCommentsAndMedia) {
        return (rs, rowNum) -> new DrinkRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("drink_name"),
                rs.getObject("place_id", UUID.class),
                rs.getString("shop_name_snapshot"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getBigDecimal("price"),
                rs.getString("currency") == null ? null : rs.getString("currency").trim(),
                rs.getBigDecimal("rating"),
                includeCommentsAndMedia ? rs.getString("comment") : null,
                nullableInteger(rs, "sweetness_level"),
                nullableInteger(rs, "ice_level"),
                nullableBoolean(rs, "would_buy_again"),
                FoodRecordVisibility.valueOf(rs.getString("visibility")),
                rs.getObject("group_id", UUID.class),
                includeCommentsAndMedia ? rs.getObject("media_asset_id", UUID.class) : null,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getLong("version"));
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
