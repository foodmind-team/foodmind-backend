package com.foodmind.foodmindbackend.shopping.application;

import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingListPage;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListShoppingLists {
    private final ShoppingListRepository repository;

    public ListShoppingLists(ShoppingListRepository repository) {
        this.repository = repository;
    }

    public ShoppingListPage handle(UUID userId, String status, int page, int size) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalized != null && !normalized.equals("OPEN") && !normalized.equals("COMPLETED")) {
            throw new com.foodmind.foodmindbackend.common.error.ApiException(
                    com.foodmind.foodmindbackend.common.error.ErrorCode.VALIDATION_ERROR,
                    "Shopping-list status must be OPEN or COMPLETED.");
        }
        return repository.findOwnedPage(userId, normalized, page, size);
    }
}
