package com.foodmind.foodmindbackend.search.application.port;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Batch lookup for READY media attached to already-authorised search results. */
public interface ReadyMediaQuery {
    Map<UUID, UUID> findReadyFoodMedia(Set<UUID> foodRecordIds);
}
