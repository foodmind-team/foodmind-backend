package com.foodmind.foodmindbackend.shopping.api.request;

import com.foodmind.foodmindbackend.shopping.application.UpdateShoppingListItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateShoppingListItemRequest(
        boolean checked,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal purchasedQuantity,
        @NotBlank @Size(max = 16) String unit,
        LocalDate expiryDate) {

    public UpdateShoppingListItem.Command toCommand() {
        return new UpdateShoppingListItem.Command(checked, purchasedQuantity, unit, expiryDate);
    }
}
