package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically finishes a READY plan and applies its immutable lot allocations exactly once. */
@Service
public class ConsumeCookingPlanInventory {
    private final NamedParameterJdbcTemplate jdbc;
    public ConsumeCookingPlanInventory(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Result handle(UUID userId, UUID planId) {
        PlanState plan = jdbc.query(
                "SELECT status,finished_at FROM cooking_plan WHERE id=:planId AND user_id=:userId FOR UPDATE",
                new MapSqlParameterSource().addValue("planId", planId).addValue("userId", userId),
                (rs, n) -> new PlanState(
                        rs.getString("status"),
                        rs.getObject("finished_at", OffsetDateTime.class)))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"READY".equals(plan.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "Only a ready cooking plan can be finished.");
        }
        var rows = jdbc.query("SELECT al.id,al.inventory_lot_id,al.quantity FROM cooking_plan_lot_allocation al JOIN cooking_plan_completion_item ci ON ci.id=al.completion_item_id WHERE ci.plan_id=:planId AND al.inventory_lot_id IS NOT NULL ORDER BY al.inventory_lot_id,al.id", new MapSqlParameterSource().addValue("planId", planId), (rs,n) -> new Allocation(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getBigDecimal(3)));
        if (plan.finishedAt() != null) {
            Integer alreadyConsumed = jdbc.queryForObject(
                    "SELECT count(*) FROM cooking_plan_inventory_consumption WHERE plan_id=:planId",
                    new MapSqlParameterSource("planId", planId),
                    Integer.class);
            return new Result(planId, rows.size(), alreadyConsumed == null ? 0 : alreadyConsumed);
        }
        int consumed = 0;
        for (Allocation row : rows) {
            int claimed = jdbc.update("INSERT INTO cooking_plan_inventory_consumption (id,plan_id,allocation_id,quantity) VALUES (:id,:planId,:allocationId,:quantity) ON CONFLICT (allocation_id) DO NOTHING", new MapSqlParameterSource().addValue("id",UUID.randomUUID()).addValue("planId",planId).addValue("allocationId",row.id()).addValue("quantity",row.quantity()));
            if (claimed == 0) continue;
            int updated = jdbc.update("""
                    UPDATE inventory_lot
                    SET on_hand = on_hand - :quantity,
                        archived_at = CASE
                            WHEN on_hand - :quantity = 0 THEN CURRENT_TIMESTAMP
                            ELSE archived_at
                        END,
                        version = version + 1
                    WHERE id = :lotId
                      AND user_id = :userId
                      AND archived_at IS NULL
                      AND on_hand - reserved >= :quantity
                    """, new MapSqlParameterSource()
                    .addValue("quantity", row.quantity())
                    .addValue("lotId", row.lotId())
                    .addValue("userId", userId));
            if (updated != 1) throw new ApiException(ErrorCode.CONFLICT, "Inventory changed; regenerate the plan before consuming it.");
            consumed++;
        }
        jdbc.update(
                "UPDATE cooking_plan SET finished_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:planId AND user_id=:userId AND finished_at IS NULL",
                new MapSqlParameterSource().addValue("planId", planId).addValue("userId", userId));
        return new Result(planId, rows.size(), consumed);
    }
    private record PlanState(String status, OffsetDateTime finishedAt) { }
    private record Allocation(UUID id, UUID lotId, BigDecimal quantity) { }
    public record Result(UUID planId, int allocationCount, int consumedAllocationCount) { }
}
