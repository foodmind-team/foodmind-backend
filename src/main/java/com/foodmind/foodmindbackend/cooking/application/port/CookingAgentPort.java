package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSubmission;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import java.util.List;
import java.util.Map;

/**
 * Port for invoking the cooking-plan agent over its native internal contract
 * ({@code POST /internal/v1/agents/cooking-plan/generate}, X-Internal-Token).
 */
public interface CookingAgentPort {

    CookingAgentResult generate(AgentGeneratePlanRequest request);

    /**
     * Reuses the agent's NL parsing + gap-filling pipeline
     * ({@code POST .../preprocess}). Raw recipe text in, fully-populated
     * structured candidates out — the backend then passes them back on the
     * generate request as {@code preparsed_candidates} so the agent never
     * re-parses or re-asks gap/assumption questions.
     */
    List<Map<String, Object>> preprocess(List<AgentRecipeInput> recipes);

    /** Submits an async task ({@code POST .../tasks}); throws {@code CookingAgentTaskException} on failure. */
    AgentTaskSubmission submitTask(AgentGeneratePlanRequest request);

    /** Reads a task snapshot ({@code GET .../tasks/{id}}); throws {@code CookingAgentTaskException} on failure. */
    AgentTaskSnapshot getTask(String taskId);

    /** Cancels a task ({@code POST .../tasks/{id}/cancel}); throws {@code CookingAgentTaskException} on failure. */
    AgentTaskSnapshot cancelTask(String taskId);
}
