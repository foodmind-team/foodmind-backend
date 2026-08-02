package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentKitchenResourceSnapshot;
import java.util.List;
import java.util.UUID;

/** Reads the user's kitchen resources for the agent request snapshot. */
public interface KitchenResourceQuery {

    List<AgentKitchenResourceSnapshot> resources(UUID userId);
}
