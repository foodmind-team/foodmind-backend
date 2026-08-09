package com.foodmind.foodmindbackend.inventory.api.request;

import com.foodmind.foodmindbackend.inventory.application.InventoryLotCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryLotRequest(
        @NotBlank @Size(max = 128) String ingredientName,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotBlank @Size(max = 16) String unit,
        LocalDate expiryDate) {

    public InventoryLotCommand toCommand() {
        return new InventoryLotCommand(ingredientName, quantity, unit, expiryDate);
    }
}
