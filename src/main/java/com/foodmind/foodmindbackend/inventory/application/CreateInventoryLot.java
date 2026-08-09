package com.foodmind.foodmindbackend.inventory.application;

import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateInventoryLot {
    private final InventoryLotRepository repository;
    private final Clock clock;

    public CreateInventoryLot(InventoryLotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public InventoryLot handle(UUID userId, InventoryLotCommand rawCommand) {
        InventoryLotCommand command = InventoryLotValidation.requireValid(rawCommand);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return repository.create(new InventoryLot(
                UUID.randomUUID(), null, userId, command.ingredientName(), command.quantity(),
                BigDecimal.ZERO, command.unit(), command.expiryDate(), now, now, now, null, 0));
    }
}
