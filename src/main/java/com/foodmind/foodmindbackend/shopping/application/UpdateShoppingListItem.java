package com.foodmind.foodmindbackend.shopping.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateShoppingListItem {
    private final ShoppingListRepository repository;
    private final Clock clock;

    public UpdateShoppingListItem(ShoppingListRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ShoppingList handle(
            UUID userId,
            UUID shoppingListId,
            UUID itemId,
            long expectedVersion,
            Command command) {
        if (command == null || command.purchasedQuantity() == null
                || command.purchasedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Purchased quantity must be greater than zero.");
        }
        if (command.unit() == null || command.unit().isBlank() || command.unit().trim().length() > 16) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unit is required and must be at most 16 characters.");
        }
        return repository.updateItem(
                userId, shoppingListId, itemId, expectedVersion, command.checked(),
                command.purchasedQuantity(), command.unit().trim(), command.expiryDate(), OffsetDateTime.now(clock))
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                        "Shopping-list item changed or the list is already completed; reload before saving."));
    }

    public record Command(boolean checked, BigDecimal purchasedQuantity, String unit, LocalDate expiryDate) {
    }
}
