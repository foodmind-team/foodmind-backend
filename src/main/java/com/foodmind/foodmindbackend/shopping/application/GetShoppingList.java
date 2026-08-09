package com.foodmind.foodmindbackend.shopping.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetShoppingList {
    private final ShoppingListRepository repository;

    public GetShoppingList(ShoppingListRepository repository) {
        this.repository = repository;
    }

    public ShoppingList handle(UUID userId, UUID shoppingListId) {
        return repository.findOwned(userId, shoppingListId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Shopping list was not found."));
    }
}
