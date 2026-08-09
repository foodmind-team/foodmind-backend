package com.foodmind.foodmindbackend.shopping.api.response;

import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ShoppingListResponse(
        UUID shoppingListId,
        UUID sourcePlanId,
        UUID rootPlanId,
        int originalServings,
        UUID continuationPlanId,
        String status,
        int checkedItemCount,
        int totalItemCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        long version,
        List<ItemResponse> items) {

    public static ShoppingListResponse from(ShoppingList list) {
        return new ShoppingListResponse(
                list.id(), list.sourcePlanId(), list.rootPlanId(), list.originalServings(),
                list.continuationPlanId(), list.status(),
                (int) list.items().stream().filter(ShoppingList.Item::checked).count(), list.items().size(),
                list.createdAt(), list.updatedAt(), list.completedAt(), list.version(),
                list.items().stream().map(ItemResponse::from).toList());
    }

    public record ItemResponse(
            UUID itemId,
            int sequenceNo,
            String ingredientName,
            BigDecimal requiredQuantity,
            BigDecimal purchasedQuantity,
            String unit,
            LocalDate expiryDate,
            boolean checked,
            long version) {

        static ItemResponse from(ShoppingList.Item item) {
            return new ItemResponse(
                    item.id(), item.sequenceNo(), item.ingredientName(), item.requiredQuantity(),
                    item.purchasedQuantity(), item.unit(), item.expiryDate(), item.checked(), item.version());
        }
    }
}
