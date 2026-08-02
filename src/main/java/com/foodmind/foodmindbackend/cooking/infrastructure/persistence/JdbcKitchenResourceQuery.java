package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import com.foodmind.foodmindbackend.cooking.application.port.KitchenResourceQuery;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentKitchenResourceSnapshot;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the user's kitchen resources as agent snapshots. */
@Repository
public class JdbcKitchenResourceQuery implements KitchenResourceQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcKitchenResourceQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentKitchenResourceSnapshot> resources(UUID userId) {
        return jdbcTemplate.query("""
                SELECT id,
                       resource_type,
                       capacity,
                       capacity_unit,
                       capabilities,
                       available
                FROM kitchen_resource
                WHERE user_id = :userId
                ORDER BY resource_type, id
                """,
                new MapSqlParameterSource("userId", userId),
                this::row);
    }

    private AgentKitchenResourceSnapshot row(ResultSet rs, int rowNum) throws SQLException {
        return new AgentKitchenResourceSnapshot(
                rs.getObject("id", UUID.class).toString(),
                rs.getString("resource_type"),
                rs.getBigDecimal("capacity"),
                rs.getString("capacity_unit"),
                stringArray(rs.getArray("capabilities")),
                rs.getBoolean("available"));
    }

    private List<String> stringArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String.class::cast).toList();
    }
}
