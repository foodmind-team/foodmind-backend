package com.foodmind.foodmindbackend.shopping.infrastructure.persistence;

import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingListPage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShoppingListRepository implements ShoppingListRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShoppingListRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ShoppingList createIfAbsent(ShoppingList list) {
        int inserted = jdbc.update("""
                INSERT INTO shopping_list (
                    id, user_id, source_plan_id, root_plan_id, original_servings,
                    status, created_at, updated_at, version
                ) VALUES (
                    :id, :userId, :sourcePlanId, :rootPlanId, :originalServings,
                    'OPEN', :createdAt, :updatedAt, 0
                )
                ON CONFLICT (user_id, source_plan_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", list.id())
                .addValue("userId", list.userId())
                .addValue("sourcePlanId", list.sourcePlanId())
                .addValue("rootPlanId", list.rootPlanId())
                .addValue("originalServings", list.originalServings())
                .addValue("createdAt", list.createdAt())
                .addValue("updatedAt", list.updatedAt()));
        if (inserted > 0) {
            MapSqlParameterSource[] batch = list.items().stream()
                    .map(item -> new MapSqlParameterSource()
                            .addValue("id", item.id())
                            .addValue("shoppingListId", list.id())
                            .addValue("sequenceNo", item.sequenceNo())
                            .addValue("ingredientName", item.ingredientName())
                            .addValue("requiredQuantity", item.requiredQuantity())
                            .addValue("purchasedQuantity", item.purchasedQuantity())
                            .addValue("unit", item.unit())
                            .addValue("createdAt", item.createdAt())
                            .addValue("updatedAt", item.updatedAt()))
                    .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO shopping_list_item (
                        id, shopping_list_id, sequence_no, ingredient_name,
                        required_quantity, purchased_quantity, unit, created_at, updated_at
                    ) VALUES (
                        :id, :shoppingListId, :sequenceNo, :ingredientName,
                        :requiredQuantity, :purchasedQuantity, :unit, :createdAt, :updatedAt
                    )
                    """, batch);
        }
        return findOwnedBySourcePlan(list.userId(), list.sourcePlanId()).orElseThrow();
    }

    @Override
    public Optional<ShoppingList> findOwned(UUID userId, UUID shoppingListId) {
        return findLists("sl.id = :id AND sl.user_id = :userId",
                new MapSqlParameterSource().addValue("id", shoppingListId).addValue("userId", userId))
                .stream().findFirst();
    }

    @Override
    public Optional<ShoppingList> findOwnedBySourcePlan(UUID userId, UUID sourcePlanId) {
        return findLists("sl.source_plan_id = :sourcePlanId AND sl.user_id = :userId",
                new MapSqlParameterSource().addValue("sourcePlanId", sourcePlanId).addValue("userId", userId))
                .stream().findFirst();
    }

    @Override
    public ShoppingListPage findOwnedPage(UUID userId, String status, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("status", status)
                .addValue("limit", size)
                .addValue("offset", page * size);
        String statusFilter = status == null ? "" : " AND sl.status = :status";
        List<ShoppingList> lists = findListsPage(statusFilter, params);
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM shopping_list sl
                WHERE sl.user_id = :userId
                """ + statusFilter, params, Long.class);
        return new ShoppingListPage(lists, total == null ? 0 : total);
    }

    @Override
    public Optional<ShoppingList> updateItem(
            UUID userId,
            UUID shoppingListId,
            UUID itemId,
            long expectedVersion,
            boolean checked,
            java.math.BigDecimal purchasedQuantity,
            String unit,
            LocalDate expiryDate,
            OffsetDateTime updatedAt) {
        int changed = jdbc.update("""
                UPDATE shopping_list_item item
                SET checked = :checked,
                    purchased_quantity = :purchasedQuantity,
                    unit = :unit,
                    expiry_date = :expiryDate,
                    updated_at = :updatedAt,
                    version = item.version + 1
                FROM shopping_list list
                WHERE item.id = :itemId
                  AND item.shopping_list_id = :shoppingListId
                  AND list.id = item.shopping_list_id
                  AND list.user_id = :userId
                  AND list.status = 'OPEN'
                  AND item.version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("checked", checked)
                .addValue("purchasedQuantity", purchasedQuantity)
                .addValue("unit", unit)
                .addValue("expiryDate", expiryDate)
                .addValue("updatedAt", updatedAt)
                .addValue("itemId", itemId)
                .addValue("shoppingListId", shoppingListId)
                .addValue("userId", userId)
                .addValue("expectedVersion", expectedVersion));
        return changed == 0 ? Optional.empty() : findOwned(userId, shoppingListId);
    }

    @Override
    public Optional<ShoppingList> lockOwned(UUID userId, UUID shoppingListId) {
        List<UUID> locked = jdbc.query("""
                SELECT id FROM shopping_list
                WHERE id = :id AND user_id = :userId
                FOR UPDATE
                """, new MapSqlParameterSource().addValue("id", shoppingListId).addValue("userId", userId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        jdbc.query("""
                SELECT id FROM shopping_list_item
                WHERE shopping_list_id = :id
                ORDER BY id
                FOR UPDATE
                """, new MapSqlParameterSource("id", shoppingListId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        return findOwned(userId, shoppingListId);
    }

    @Override
    public void completeAndLinkLots(
            UUID userId,
            UUID shoppingListId,
            List<ItemLotLink> links,
            OffsetDateTime completedAt) {
        for (ItemLotLink link : links) {
            jdbc.update("""
                    UPDATE shopping_list_item
                    SET inventory_lot_id = :lotId,
                        updated_at = :completedAt,
                        version = version + 1
                    WHERE id = :itemId AND shopping_list_id = :shoppingListId
                    """, new MapSqlParameterSource()
                    .addValue("lotId", link.inventoryLotId())
                    .addValue("completedAt", completedAt)
                    .addValue("itemId", link.itemId())
                    .addValue("shoppingListId", shoppingListId));
        }
        int changed = jdbc.update("""
                UPDATE shopping_list
                SET status = 'COMPLETED',
                    completed_at = :completedAt,
                    updated_at = :completedAt,
                    version = version + 1
                WHERE id = :id AND user_id = :userId AND status = 'OPEN'
                """, new MapSqlParameterSource()
                .addValue("id", shoppingListId)
                .addValue("userId", userId)
                .addValue("completedAt", completedAt));
        if (changed != 1) {
            throw new IllegalStateException("Shopping list could not be completed.");
        }
    }

    @Override
    public boolean attachContinuation(UUID userId, UUID shoppingListId, UUID continuationPlanId) {
        return jdbc.update("""
                UPDATE shopping_list
                SET continuation_plan_id = :continuationPlanId,
                    version = version + 1
                WHERE id = :id
                  AND user_id = :userId
                  AND status = 'COMPLETED'
                  AND continuation_plan_id IS NULL
                """, new MapSqlParameterSource()
                .addValue("id", shoppingListId)
                .addValue("userId", userId)
                .addValue("continuationPlanId", continuationPlanId)) == 1;
    }

    private List<ShoppingList> findLists(String predicate, MapSqlParameterSource params) {
        List<ListRow> rows = jdbc.query(selectLists() + " WHERE " + predicate, params, this::listRow);
        return assemble(rows);
    }

    private List<ShoppingList> findListsPage(String statusFilter, MapSqlParameterSource params) {
        List<ListRow> rows = jdbc.query(selectLists() + """
                WHERE sl.user_id = :userId
                """ + statusFilter + """
                ORDER BY sl.updated_at DESC, sl.id DESC
                LIMIT :limit OFFSET :offset
                """, params, this::listRow);
        return assemble(rows);
    }

    private List<ShoppingList> assemble(List<ListRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(ListRow::id).toList();
        Map<UUID, List<ShoppingList.Item>> items = new LinkedHashMap<>();
        ids.forEach(id -> items.put(id, new ArrayList<>()));
        jdbc.query("""
                SELECT id, shopping_list_id, sequence_no, ingredient_name, required_quantity,
                       purchased_quantity, unit, expiry_date, checked, inventory_lot_id,
                       created_at, updated_at, version
                FROM shopping_list_item
                WHERE shopping_list_id IN (:ids)
                ORDER BY shopping_list_id, sequence_no
                """, new MapSqlParameterSource("ids", ids), (rs, rowNum) -> {
            UUID listId = rs.getObject("shopping_list_id", UUID.class);
            items.get(listId).add(itemRow(rs));
            return null;
        });
        return rows.stream().map(row -> new ShoppingList(
                row.id(), row.userId(), row.sourcePlanId(), row.rootPlanId(), row.originalServings(),
                row.continuationPlanId(), row.status(), row.createdAt(), row.updatedAt(), row.completedAt(),
                row.version(), items.getOrDefault(row.id(), List.of()))).toList();
    }

    private String selectLists() {
        return """
                SELECT sl.id, sl.user_id, sl.source_plan_id, sl.root_plan_id, sl.original_servings,
                       sl.continuation_plan_id, sl.status, sl.created_at, sl.updated_at,
                       sl.completed_at, sl.version
                FROM shopping_list sl
                """;
    }

    private ListRow listRow(ResultSet rs, int rowNum) throws SQLException {
        return new ListRow(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("source_plan_id", UUID.class), rs.getObject("root_plan_id", UUID.class),
                rs.getInt("original_servings"), rs.getObject("continuation_plan_id", UUID.class),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getLong("version"));
    }

    private ShoppingList.Item itemRow(ResultSet rs) throws SQLException {
        return new ShoppingList.Item(
                rs.getObject("id", UUID.class), rs.getObject("shopping_list_id", UUID.class),
                rs.getInt("sequence_no"), rs.getString("ingredient_name"),
                rs.getBigDecimal("required_quantity"), rs.getBigDecimal("purchased_quantity"),
                rs.getString("unit"), rs.getObject("expiry_date", LocalDate.class),
                rs.getBoolean("checked"), rs.getObject("inventory_lot_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
    }

    private record ListRow(
            UUID id,
            UUID userId,
            UUID sourcePlanId,
            UUID rootPlanId,
            int originalServings,
            UUID continuationPlanId,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime completedAt,
            long version) {
    }
}
