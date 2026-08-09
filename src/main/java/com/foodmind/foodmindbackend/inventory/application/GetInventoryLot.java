package com.foodmind.foodmindbackend.inventory.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetInventoryLot {
    private final InventoryLotRepository repository;

    public GetInventoryLot(InventoryLotRepository repository) {
        this.repository = repository;
    }

    public InventoryLot handle(UUID userId, UUID lotId) {
        return repository.findOwned(userId, lotId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Inventory lot was not found."));
    }
}
