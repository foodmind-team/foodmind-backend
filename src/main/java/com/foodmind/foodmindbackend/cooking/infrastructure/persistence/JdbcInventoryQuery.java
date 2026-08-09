package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import com.foodmind.foodmindbackend.cooking.application.port.InventoryQuery;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInventoryLotSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the user's usable inventory lots in FEFO order
 * (earliest expiry first, then smallest on-hand) as agent snapshots.
 */
@Repository
public class JdbcInventoryQuery implements InventoryQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInventoryQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentInventoryLotSnapshot> lots(UUID userId) {
        return jdbcTemplate.query("""
                SELECT il.id      AS lot_id,
                       il.item_id,
                       ii.canonical_name,
                       il.on_hand,
                       il.reserved,
                       il.unit,
                       il.expiry_date
                FROM inventory_lot il
                JOIN inventory_item ii ON ii.id = il.item_id
                WHERE il.user_id = :userId
                  AND il.archived_at IS NULL
                  AND (il.on_hand - il.reserved) > 0
                ORDER BY il.expiry_date NULLS LAST, il.on_hand, il.id
                """,
                new MapSqlParameterSource("userId", userId),
                this::row);
    }

    private AgentInventoryLotSnapshot row(ResultSet rs, int rowNum) throws SQLException {
        return new AgentInventoryLotSnapshot(
                rs.getObject("lot_id", UUID.class).toString(),
                rs.getObject("item_id", UUID.class).toString(),
                rs.getString("canonical_name"),
                rs.getBigDecimal("on_hand"),
                rs.getBigDecimal("reserved"),
                rs.getString("unit"),
                rs.getObject("expiry_date", LocalDate.class));
    }
}
