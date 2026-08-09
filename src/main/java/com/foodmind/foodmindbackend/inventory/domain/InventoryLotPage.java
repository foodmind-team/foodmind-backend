package com.foodmind.foodmindbackend.inventory.domain;

import java.util.List;

public record InventoryLotPage(List<InventoryLot> items, long totalItems) {
    public InventoryLotPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
