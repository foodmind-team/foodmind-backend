package com.foodmind.foodmindbackend.inventory.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;

final class InventoryLotValidation {
    private InventoryLotValidation() {
    }

    static InventoryLotCommand requireValid(InventoryLotCommand command) {
        if (command == null || command.ingredientName() == null || command.ingredientName().isBlank()
                || command.ingredientName().trim().length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ingredient name is required and must be at most 128 characters.");
        }
        if (command.quantity() == null || command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Inventory quantity must be greater than zero.");
        }
        if (command.unit() == null || command.unit().isBlank() || command.unit().trim().length() > 16) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Inventory unit is required and must be at most 16 characters.");
        }
        return new InventoryLotCommand(
                command.ingredientName().trim(),
                command.quantity(),
                command.unit().trim(),
                command.expiryDate());
    }
}
