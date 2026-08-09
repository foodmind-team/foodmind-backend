package com.foodmind.foodmindbackend.inventory.application;

import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLotPage;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListInventoryLots {
    private final InventoryLotRepository repository;

    public ListInventoryLots(InventoryLotRepository repository) {
        this.repository = repository;
    }

    public InventoryLotPage handle(UUID userId, int page, int size) {
        return repository.findOwnedPage(userId, page, size);
    }
}
