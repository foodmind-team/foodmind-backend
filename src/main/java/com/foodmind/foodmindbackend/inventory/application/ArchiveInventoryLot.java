package com.foodmind.foodmindbackend.inventory.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.inventory.application.port.InventoryLotRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveInventoryLot {
    private final InventoryLotRepository repository;
    private final Clock clock;

    public ArchiveInventoryLot(InventoryLotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void handle(UUID userId, UUID lotId, long expectedVersion) {
        if (!repository.archive(userId, lotId, expectedVersion, OffsetDateTime.now(clock))) {
            if (repository.findOwned(userId, lotId).isEmpty()) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Inventory lot was not found.");
            }
            throw new ApiException(ErrorCode.CONFLICT, "Inventory changed; reload before archiving.");
        }
    }
}
