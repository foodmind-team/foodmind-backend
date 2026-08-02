package com.foodmind.foodmindbackend.cooking.domain.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Terminal plan response hierarchy, discriminated by the agent's {@code status} field
 * (READY / NEEDS_CONFIRMATION / INFEASIBLE / FAILED). Field names use snake_case JSON.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentReadyPlanResponse.class, name = "READY"),
        @JsonSubTypes.Type(value = AgentConfirmationPlanResponse.class, name = "NEEDS_CONFIRMATION"),
        @JsonSubTypes.Type(value = AgentInfeasiblePlanResponse.class, name = "INFEASIBLE"),
        @JsonSubTypes.Type(value = AgentFailedPlanResponse.class, name = "FAILED")
})
public interface AgentPlanResponse {

    String status();
}
