package com.foodmind.foodmindbackend.shopping.domain;

import java.util.List;

public record ShoppingListPage(List<ShoppingList> items, long totalItems) {
    public ShoppingListPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
