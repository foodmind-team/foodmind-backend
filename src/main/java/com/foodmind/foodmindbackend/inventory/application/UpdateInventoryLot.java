package com.foodmind.foodmindbackend.inventory.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateInventoryLot {
    private final InventoryLotRepository repository;
    private final Clock clock;

    public UpdateInventoryLot(InventoryLotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public InventoryLot handle(UUID userId, UUID lotId, long expectedVersion, InventoryLotCommand rawCommand) {
        InventoryLotCommand command = InventoryLotValidation.requireValid(rawCommand);
        InventoryLot current = repository.findOwned(userId, lotId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Inventory lot was not found."));
        if (command.quantity().compareTo(current.reserved()) < 0) {
            throw new ApiException(ErrorCode.CONFLICT, "Quantity cannot be lower than the reserved amount.");
        }
        InventoryLot next = new InventoryLot(
                current.id(), current.itemId(), current.userId(), command.ingredientName(), command.quantity(),
                current.reserved(), command.unit(), command.expiryDate(), current.purchasedAt(),
                current.createdAt(), OffsetDateTime.now(clock), null, current.version() + 1);
        return repository.update(next, expectedVersion)
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "Inventory changed; reload before saving."));
    }
}
