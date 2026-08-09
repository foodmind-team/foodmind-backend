package com.foodmind.foodmindbackend.shopping.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Persisted purchase checklist derived from a cooking-plan inventory shortage. */
public record ShoppingList(
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
        long version,
        List<Item> items) {

    public ShoppingList {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean allChecked() {
        return !items.isEmpty() && items.stream().allMatch(Item::checked);
    }

    public record Item(
            UUID id,
            UUID shoppingListId,
            int sequenceNo,
            String ingredientName,
            BigDecimal requiredQuantity,
            BigDecimal purchasedQuantity,
            String unit,
            LocalDate expiryDate,
            boolean checked,
            UUID inventoryLotId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            long version) {
    }
}
