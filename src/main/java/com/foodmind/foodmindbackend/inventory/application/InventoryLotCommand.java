package com.foodmind.foodmindbackend.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryLotCommand(
        String ingredientName,
        BigDecimal quantity,
        String unit,
        LocalDate expiryDate) {
}
