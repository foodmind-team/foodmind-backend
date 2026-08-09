package com.foodmind.foodmindbackend.inventory.api.response;

import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryLotResponse(
        UUID lotId,
        String ingredientName,
        BigDecimal quantity,
        BigDecimal reserved,
        BigDecimal available,
        String unit,
        LocalDate expiryDate,
        OffsetDateTime purchasedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {

    public static InventoryLotResponse from(InventoryLot lot) {
        return new InventoryLotResponse(
                lot.id(), lot.ingredientName(), lot.quantity(), lot.reserved(), lot.available(), lot.unit(),
                lot.expiryDate(), lot.purchasedAt(), lot.createdAt(), lot.updatedAt(), lot.version());
    }
}
