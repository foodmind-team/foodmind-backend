package com.foodmind.foodmindbackend.inventory.application.port;

import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLotPage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLotRepository {
    InventoryLot create(InventoryLot lot);

    List<InventoryLot> createAll(List<InventoryLot> lots);

    Optional<InventoryLot> findOwned(UUID userId, UUID lotId);

    InventoryLotPage findOwnedPage(UUID userId, int page, int size);

    Optional<InventoryLot> update(InventoryLot lot, long expectedVersion);

    boolean archive(UUID userId, UUID lotId, long expectedVersion, OffsetDateTime archivedAt);
}
