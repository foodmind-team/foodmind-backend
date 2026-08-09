package com.foodmind.foodmindbackend.inventory.infrastructure.persistence;

import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLotPage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryLotRepository implements InventoryLotRepository {

    private static final String SELECT_COLUMNS = """
            SELECT il.id, il.item_id, il.user_id, ii.canonical_name, il.on_hand, il.reserved,
                   il.unit, il.expiry_date, il.purchased_at, il.created_at, il.updated_at,
                   il.archived_at, il.version
            FROM inventory_lot il
            JOIN inventory_item ii ON ii.id = il.item_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInventoryLotRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InventoryLot create(InventoryLot lot) {
        UUID itemId = resolveItem(lot.ingredientName(), lot.unit());
        jdbc.update("""
                INSERT INTO inventory_lot (
                    id, item_id, user_id, on_hand, reserved, unit, expiry_date,
                    purchased_at, created_at, updated_at, version
                ) VALUES (
                    :id, :itemId, :userId, :quantity, :reserved, :unit, :expiryDate,
                    :purchasedAt, :createdAt, :updatedAt, :version
                )
                """, params(lot).addValue("itemId", itemId));
        return findOwned(lot.userId(), lot.id()).orElseThrow();
    }

    @Override
    public List<InventoryLot> createAll(List<InventoryLot> lots) {
        for (InventoryLot lot : lots) {
            create(lot);
        }
        return lots.stream()
                .map(lot -> findOwned(lot.userId(), lot.id()).orElseThrow())
                .toList();
    }

    @Override
    public Optional<InventoryLot> findOwned(UUID userId, UUID lotId) {
        return jdbc.query(SELECT_COLUMNS + """
                WHERE il.id = :id AND il.user_id = :userId AND il.archived_at IS NULL
                """, new MapSqlParameterSource().addValue("id", lotId).addValue("userId", userId), mapper())
                .stream().findFirst();
    }

    @Override
    public InventoryLotPage findOwnedPage(UUID userId, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
                .addValue("limit", size)
                .addValue("offset", page * size);
        List<InventoryLot> items = jdbc.query(SELECT_COLUMNS + """
                WHERE il.user_id = :userId AND il.archived_at IS NULL
                ORDER BY il.expiry_date NULLS LAST, il.created_at DESC, il.id
                LIMIT :limit OFFSET :offset
                """, params, mapper());
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_lot
                WHERE user_id = :userId AND archived_at IS NULL
                """, new MapSqlParameterSource("userId", userId), Long.class);
        return new InventoryLotPage(items, total == null ? 0 : total);
    }

    @Override
    public Optional<InventoryLot> update(InventoryLot lot, long expectedVersion) {
        UUID itemId = resolveItem(lot.ingredientName(), lot.unit());
        int changed = jdbc.update("""
                UPDATE inventory_lot
                SET item_id = :itemId,
                    on_hand = :quantity,
                    unit = :unit,
                    expiry_date = :expiryDate,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id
                  AND user_id = :userId
                  AND archived_at IS NULL
                  AND version = :expectedVersion
                """, params(lot).addValue("itemId", itemId).addValue("expectedVersion", expectedVersion));
        return changed == 0 ? Optional.empty() : findOwned(lot.userId(), lot.id());
    }

    @Override
    public boolean archive(UUID userId, UUID lotId, long expectedVersion, OffsetDateTime archivedAt) {
        return jdbc.update("""
                UPDATE inventory_lot
                SET archived_at = :archivedAt,
                    updated_at = :archivedAt,
                    version = version + 1
                WHERE id = :id
                  AND user_id = :userId
                  AND archived_at IS NULL
                  AND version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", lotId)
                .addValue("userId", userId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("archivedAt", archivedAt)) > 0;
    }

    private UUID resolveItem(String ingredientName, String unit) {
        UUID candidateId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_item (id, canonical_name, default_unit)
                VALUES (:id, :name, :unit)
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", candidateId)
                .addValue("name", ingredientName)
                .addValue("unit", unit));
        return jdbc.queryForObject("""
                SELECT id FROM inventory_item WHERE lower(canonical_name) = lower(:name)
                """, new MapSqlParameterSource("name", ingredientName), UUID.class);
    }

    private MapSqlParameterSource params(InventoryLot lot) {
        return new MapSqlParameterSource()
                .addValue("id", lot.id())
                .addValue("userId", lot.userId())
                .addValue("quantity", lot.quantity())
                .addValue("reserved", lot.reserved())
                .addValue("unit", lot.unit())
                .addValue("expiryDate", lot.expiryDate())
                .addValue("purchasedAt", lot.purchasedAt())
                .addValue("createdAt", lot.createdAt())
                .addValue("updatedAt", lot.updatedAt())
                .addValue("version", lot.version());
    }

    private RowMapper<InventoryLot> mapper() {
        return this::row;
    }

    private InventoryLot row(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryLot(
                rs.getObject("id", UUID.class),
                rs.getObject("item_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("canonical_name"),
                rs.getBigDecimal("on_hand"),
                rs.getBigDecimal("reserved"),
                rs.getString("unit"),
                rs.getObject("expiry_date", LocalDate.class),
                rs.getObject("purchased_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("archived_at", OffsetDateTime.class),
                rs.getLong("version"));
    }
}
