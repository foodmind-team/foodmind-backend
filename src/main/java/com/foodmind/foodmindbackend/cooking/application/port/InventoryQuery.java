package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInventoryLotSnapshot;
import java.util.List;
import java.util.UUID;

/** Reads the user's inventory lots (FEFO-ordered) for the agent request snapshot. */
public interface InventoryQuery {

    List<AgentInventoryLotSnapshot> lots(UUID userId);
}
