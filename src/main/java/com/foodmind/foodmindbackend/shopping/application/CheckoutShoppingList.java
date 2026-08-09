package com.foodmind.foodmindbackend.shopping.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short database-only checkout. No Agent/network work is allowed in this transaction. */
@Service
public class CheckoutShoppingList {
    private final ShoppingListRepository shoppingLists;
    private final InventoryLotRepository inventoryLots;
    private final Clock clock;

    public CheckoutShoppingList(
            ShoppingListRepository shoppingLists,
            InventoryLotRepository inventoryLots,
            Clock clock) {
        this.shoppingLists = shoppingLists;
        this.inventoryLots = inventoryLots;
        this.clock = clock;
    }

    @Transactional
    public ShoppingList handle(UUID userId, UUID shoppingListId) {
        ShoppingList list = shoppingLists.lockOwned(userId, shoppingListId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Shopping list was not found."));
        if ("COMPLETED".equals(list.status())) {
            return list;
        }
        if (!list.allChecked()) {
            throw new ApiException(ErrorCode.CONFLICT, "Check every purchased item before continuing.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<InventoryLot> requestedLots = list.items().stream()
                .map(item -> new InventoryLot(
                        UUID.randomUUID(), null, userId, item.ingredientName(), item.purchasedQuantity(),
                        BigDecimal.ZERO, item.unit(), item.expiryDate(), now, now, now, null, 0))
                .toList();
        List<InventoryLot> created = inventoryLots.createAll(requestedLots);
        List<ShoppingListRepository.ItemLotLink> links = new ArrayList<>();
        for (int index = 0; index < list.items().size(); index++) {
            links.add(new ShoppingListRepository.ItemLotLink(
                    list.items().get(index).id(), created.get(index).id()));
        }
        shoppingLists.completeAndLinkLots(userId, shoppingListId, links, now);
        return shoppingLists.findOwned(userId, shoppingListId).orElseThrow();
    }
}
