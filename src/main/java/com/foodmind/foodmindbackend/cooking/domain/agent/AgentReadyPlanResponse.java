package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Mirrors the agent's ReadyPlanResponse (native contract v1). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentReadyPlanResponse(
        String planId,
        String status,
        String solverStatus,
        int makespanMinutes,
        List<AgentTimelineTask> timeline,
        List<AgentCompletionItem> completionChecklist,
        List<AgentMiseEnPlaceItem> miseEnPlace,
        List<AgentDishCompletion> dishCompletions,
        AgentSafetyPolicy safetyPolicy,
        String explanation,
        String explanationSource,
        List<Map<String, Object>> executionFlow) implements AgentPlanResponse {

    public AgentReadyPlanResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        completionChecklist = completionChecklist == null ? List.of() : List.copyOf(completionChecklist);
        miseEnPlace = miseEnPlace == null ? List.of() : List.copyOf(miseEnPlace);
        dishCompletions = dishCompletions == null ? List.of() : List.copyOf(dishCompletions);
        executionFlow = executionFlow == null ? List.of() : List.copyOf(executionFlow);
    }
}
