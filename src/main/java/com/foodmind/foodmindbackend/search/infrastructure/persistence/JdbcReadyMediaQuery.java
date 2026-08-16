package com.foodmind.foodmindbackend.search.infrastructure.persistence;

import com.foodmind.foodmindbackend.search.application.port.ReadyMediaQuery;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Resolves media for a whole result page with one bounded query. */
@Repository
public class JdbcReadyMediaQuery implements ReadyMediaQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcReadyMediaQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UUID, UUID> findReadyFoodMedia(Set<UUID> foodRecordIds) {
        if (foodRecordIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT record.id AS source_id, record.media_asset_id
                        FROM food_record AS record
                        JOIN media_asset AS asset ON asset.id = record.media_asset_id
                        WHERE record.id IN (:sourceIds)
                          AND record.deleted_at IS NULL
                          AND asset.status = 'READY'
                        """,
                new MapSqlParameterSource("sourceIds", foodRecordIds),
                rs -> {
                    result.put(rs.getObject("source_id", UUID.class),
                            rs.getObject("media_asset_id", UUID.class));
                });
        return Map.copyOf(result);
    }
}
