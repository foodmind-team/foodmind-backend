package com.foodmind.foodmindbackend.shopping.application.port;

import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingListPage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingListRepository {
    ShoppingList createIfAbsent(ShoppingList list);

    Optional<ShoppingList> findOwned(UUID userId, UUID shoppingListId);

    Optional<ShoppingList> findOwnedBySourcePlan(UUID userId, UUID sourcePlanId);

    Optional<ShoppingList> findOwnedByRootPlan(UUID userId, UUID rootPlanId);

    ShoppingListPage findOwnedPage(UUID userId, String status, int page, int size);

    Optional<ShoppingList> updateItem(
            UUID userId,
            UUID shoppingListId,
            UUID itemId,
            long expectedVersion,
            boolean checked,
            java.math.BigDecimal purchasedQuantity,
            String unit,
            LocalDate expiryDate,
            OffsetDateTime updatedAt);

    Optional<ShoppingList> lockOwned(UUID userId, UUID shoppingListId);

    void completeAndLinkLots(
            UUID userId,
            UUID shoppingListId,
            List<ItemLotLink> links,
            OffsetDateTime completedAt);

    boolean attachContinuation(UUID userId, UUID shoppingListId, UUID continuationPlanId);

    record ItemLotLink(UUID itemId, UUID inventoryLotId) {
    }
}
