package com.foodmind.foodmindbackend.wanttotry.infrastructure.persistence;

import com.foodmind.foodmindbackend.wanttotry.application.port.WantToTryRepository;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryItem;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryPage;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySource;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySourceSummary;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Repository
public class JdbcWantToTryRepository implements WantToTryRepository {

    private static final String AUTHORISED_FOOD_RECORD = """
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

    public JdbcWantToTryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WantToTrySourceSummary> resolveSource(UUID actorUserId, WantToTrySource source) {
        return switch (source.type()) {
            case FOOD_RECORD -> resolveFoodRecord(actorUserId, source.id());
            case MEAL -> resolveMeal(source.id());
            case FOOD_PRODUCT -> resolveProduct(source.id());
            case PLACE -> resolvePlace(source.id());
        };
    }

    @Override
    public WantToTryItem insertOrResolveDuplicate(UUID ownerUserId, WantToTrySource source, String note) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO want_to_try (
                        id, user_id, source_type, food_record_id, meal_id, food_product_id, place_id, note
                    )
                    VALUES (
                        :id, :ownerUserId, :sourceType, :foodRecordId, :mealId, :foodProductId, :placeId, :note
                    )
                    %s
                    """.formatted(conflictClause(source.type())), sourceParams(id, ownerUserId, source, note));
        } catch (DataAccessException exception) {
            Optional<WantToTryItem> duplicate = findActive(ownerUserId, source);
            if (duplicate.isPresent()) {
                return duplicate.get();
            }
            throw exception;
        }
        return findActive(ownerUserId, source).orElseThrow();
    }

    private String conflictClause(WantToTrySourceType sourceType) {
        return switch (sourceType) {
            case FOOD_RECORD -> """
                    ON CONFLICT (user_id, food_record_id)
                    WHERE source_type = 'FOOD_RECORD' AND deleted_at IS NULL
                    DO NOTHING
                    """;
            case MEAL -> """
                    ON CONFLICT (user_id, meal_id)
                    WHERE source_type = 'MEAL' AND deleted_at IS NULL
                    DO NOTHING
                    """;
            case FOOD_PRODUCT -> """
                    ON CONFLICT (user_id, food_product_id)
                    WHERE source_type = 'FOOD_PRODUCT' AND deleted_at IS NULL
                    DO NOTHING
                    """;
            case PLACE -> """
                    ON CONFLICT (user_id, place_id)
                    WHERE source_type = 'PLACE' AND deleted_at IS NULL
                    DO NOTHING
                    """;
        };
    }

    @Override
    public WantToTryPage findOwnerPage(UUID ownerUserId, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("limit", size)
                .addValue("offset", Math.multiplyExact(page, size));
        Long total = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM want_to_try
                WHERE user_id = :ownerUserId
                  AND deleted_at IS NULL
                """, params, Long.class);
        List<SavedRow> rows = jdbcTemplate.query("""
                SELECT id, user_id, source_type, food_record_id, meal_id, food_product_id, place_id, note, created_at
                FROM want_to_try
                WHERE user_id = :ownerUserId
                  AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """, params, this::savedRow);
        List<WantToTryItem> items = new ArrayList<>();
        for (SavedRow row : rows) {
            Optional<WantToTrySourceSummary> summary = resolveSource(ownerUserId, row.source());
            items.add(new WantToTryItem(
                    row.id(),
                    row.ownerUserId(),
                    row.source(),
                    row.note(),
                    row.createdAt(),
                    summary.isPresent(),
                    summary.orElse(null)));
        }
        return new WantToTryPage(items, total == null ? 0 : total);
    }

    @Override
    public boolean softDeleteOwned(UUID ownerUserId, UUID id) {
        int rows = jdbcTemplate.update("""
                UPDATE want_to_try
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND user_id = :ownerUserId
                  AND deleted_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("ownerUserId", ownerUserId));
        return rows > 0;
    }

    private Optional<WantToTryItem> findActive(UUID ownerUserId, WantToTrySource source) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("sourceType", source.type().name())
                .addValue("sourceId", source.id());
        return jdbcTemplate.query("""
                SELECT id, user_id, source_type, food_record_id, meal_id, food_product_id, place_id, note, created_at
                FROM want_to_try
                WHERE user_id = :ownerUserId
                  AND source_type = :sourceType
                  AND deleted_at IS NULL
                  AND (
                      food_record_id = :sourceId
                      OR meal_id = :sourceId
                      OR food_product_id = :sourceId
                      OR place_id = :sourceId
                  )
                """, params, (rs, rowNum) -> toItem(savedRow(rs, rowNum), ownerUserId))
                .stream()
                .findFirst();
    }

    private WantToTryItem toItem(SavedRow row, UUID actorUserId) {
        Optional<WantToTrySourceSummary> summary = resolveSource(actorUserId, row.source());
        return new WantToTryItem(
                row.id(),
                row.ownerUserId(),
                row.source(),
                row.note(),
                row.createdAt(),
                summary.isPresent(),
                summary.orElse(null));
    }

    private Optional<WantToTrySourceSummary> resolveFoodRecord(UUID actorUserId, UUID sourceId) {
        return jdbcTemplate.query("""
                SELECT fr.meal_name_snapshot AS title,
                       fr.place_name_snapshot AS subtitle,
                       left(fr.comment, 500) AS snippet,
                       ma.object_key AS image_reference,
                       fr.visibility,
                       fr.owner_user_id,
                       fr.group_id,
                       fr.occurred_at
                FROM food_record fr
                LEFT JOIN media_asset ma ON ma.id = fr.media_asset_id
                WHERE fr.id = :sourceId
                  AND %s
                """.formatted(AUTHORISED_FOOD_RECORD),
                new MapSqlParameterSource()
                        .addValue("actorUserId", actorUserId)
                        .addValue("sourceId", sourceId),
                sourceSummaryRow())
                .stream()
                .findFirst();
    }

    private Optional<WantToTrySourceSummary> resolveMeal(UUID sourceId) {
        return resolveCurated("""
                SELECT m.name AS title,
                       m.meal_type AS subtitle,
                       left(m.description, 500) AS snippet,
                       NULL::text AS image_reference,
                       'CURATED'::varchar AS visibility,
                       NULL::uuid AS owner_user_id,
                       NULL::uuid AS group_id,
                       NULL::timestamptz AS occurred_at
                FROM meal m
                WHERE m.id = :sourceId
                  AND m.curation_status = 'ACTIVE'
                """, sourceId);
    }

    private Optional<WantToTrySourceSummary> resolveProduct(UUID sourceId) {
        return resolveCurated("""
                SELECT fp.name AS title,
                       fp.brand AS subtitle,
                       left(fp.description, 500) AS snippet,
                       NULL::text AS image_reference,
                       'CURATED'::varchar AS visibility,
                       NULL::uuid AS owner_user_id,
                       NULL::uuid AS group_id,
                       NULL::timestamptz AS occurred_at
                FROM food_product fp
                WHERE fp.id = :sourceId
                  AND fp.curation_status = 'ACTIVE'
                """, sourceId);
    }

    private Optional<WantToTrySourceSummary> resolvePlace(UUID sourceId) {
        return resolveCurated("""
                SELECT p.name AS title,
                       p.area AS subtitle,
                       left(p.address_text, 500) AS snippet,
                       NULL::text AS image_reference,
                       'CURATED'::varchar AS visibility,
                       NULL::uuid AS owner_user_id,
                       NULL::uuid AS group_id,
                       NULL::timestamptz AS occurred_at
                FROM place p
                WHERE p.id = :sourceId
                  AND p.curation_status = 'ACTIVE'
                """, sourceId);
    }

    private Optional<WantToTrySourceSummary> resolveCurated(String sql, UUID sourceId) {
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("sourceId", sourceId),
                sourceSummaryRow())
                .stream()
                .findFirst();
    }

    private MapSqlParameterSource sourceParams(UUID id, UUID ownerUserId, WantToTrySource source, String note) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("ownerUserId", ownerUserId)
                .addValue("sourceType", source.type().name())
                .addValue("foodRecordId", source.type() == WantToTrySourceType.FOOD_RECORD ? source.id() : null)
                .addValue("mealId", source.type() == WantToTrySourceType.MEAL ? source.id() : null)
                .addValue("foodProductId", source.type() == WantToTrySourceType.FOOD_PRODUCT ? source.id() : null)
                .addValue("placeId", source.type() == WantToTrySourceType.PLACE ? source.id() : null)
                .addValue("note", note);
    }

    private RowMapper<WantToTrySourceSummary> sourceSummaryRow() {
        return (rs, rowNum) -> new WantToTrySourceSummary(
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("snippet"),
                rs.getString("image_reference"),
                rs.getString("visibility"),
                rs.getObject("owner_user_id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class));
    }

    private SavedRow savedRow(ResultSet rs, int rowNum) throws SQLException {
        WantToTrySourceType sourceType = WantToTrySourceType.valueOf(rs.getString("source_type"));
        UUID sourceId = switch (sourceType) {
            case FOOD_RECORD -> rs.getObject("food_record_id", UUID.class);
            case MEAL -> rs.getObject("meal_id", UUID.class);
            case FOOD_PRODUCT -> rs.getObject("food_product_id", UUID.class);
            case PLACE -> rs.getObject("place_id", UUID.class);
        };
        return new SavedRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                new WantToTrySource(sourceType, sourceId),
                rs.getString("note"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private record SavedRow(
            UUID id,
            UUID ownerUserId,
            WantToTrySource source,
            String note,
            OffsetDateTime createdAt) {
    }
}
