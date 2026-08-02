package com.foodmind.foodmindbackend.recipe.domain;

import java.util.List;

public record UserRecipePage(List<UserRecipe> items, long totalItems) {
    public UserRecipePage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
