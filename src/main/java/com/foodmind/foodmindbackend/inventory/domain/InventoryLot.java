package com.foodmind.foodmindbackend.inventory.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One owner-scoped inventory batch. Historical rows are archived, not deleted. */
public record InventoryLot(
        UUID id,
        UUID itemId,
        UUID userId,
        String ingredientName,
        BigDecimal quantity,
        BigDecimal reserved,
        String unit,
        LocalDate expiryDate,
        OffsetDateTime purchasedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime archivedAt,
        long version) {

    public BigDecimal available() {
        return quantity.subtract(reserved);
    }
}
