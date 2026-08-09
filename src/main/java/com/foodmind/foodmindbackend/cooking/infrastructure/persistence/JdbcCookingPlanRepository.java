package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import com.foodmind.foodmindbackend.cooking.application.CookingPlanResultMapper;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentSafetyPolicy;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists agent-native cooking plans against the V14 schema. Write methods are
 * transactional so the agent call stays outside any database transaction.
 */
@Repository
public class JdbcCookingPlanRepository implements CookingPlanRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CookingPlanResultMapper resultMapper;

    public JdbcCookingPlanRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CookingPlanResultMapper resultMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.resultMapper = resultMapper;
    }

    @Override
    @Transactional
    public UUID createProcessing(
            UUID userId,
            AgentGeneratePlanRequest request,
            List<AgentRecipeInput> sources,
            String traceId,
            String rawRequestJson) {
        return createProcessing(userId, request, sources, traceId, rawRequestJson, null, null);
    }

    @Override
    @Transactional
    public UUID createProcessingChild(
            UUID userId,
            AgentGeneratePlanRequest request,
            List<AgentRecipeInput> sources,
            String traceId,
            String rawRequestJson,
            UUID parentPlanId,
            UUID rootPlanId) {
        if (parentPlanId == null || rootPlanId == null) {
            throw new IllegalArgumentException("Child cooking plans require parent and root plan IDs.");
        }
        return createProcessing(userId, request, sources, traceId, rawRequestJson, parentPlanId, rootPlanId);
    }

    private UUID createProcessing(
            UUID userId,
            AgentGeneratePlanRequest request,
            List<AgentRecipeInput> sources,
            String traceId,
            String rawRequestJson,
            UUID parentPlanId,
            UUID requestedRootPlanId) {
        UUID planId = UUID.randomUUID();
        UUID rootPlanId = requestedRootPlanId == null ? planId : requestedRootPlanId;
        String correlationId = sanitiseCorrelationId(traceId);
        jdbcTemplate.update("""
                INSERT INTO cooking_plan (
                    id, user_id, parent_plan_id, root_plan_id, status, agent_request_id, plan_revision, region, cooking_date,
                    serving_at, time_limit_minutes, correlation_id, agent_trace_id, schema_version,
                    request_context
                )
                VALUES (
                    :id, :userId, :parentPlanId, :rootPlanId, 'PROCESSING', :agentRequestId, :planRevision, :region, :cookingDate,
                    :servingAt, :timeLimitMinutes, :correlationId, :agentTraceId, :schemaVersion,
                    CAST(:requestContext AS jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", planId)
                        .addValue("userId", userId)
                        .addValue("parentPlanId", parentPlanId)
                        .addValue("rootPlanId", rootPlanId)
                        .addValue("agentRequestId", request.requestId())
                        .addValue("planRevision", request.planRevision())
                        .addValue("region", request.region())
                        .addValue("cookingDate", request.cookingDate())
                        .addValue("servingAt", request.servingAt())
                        .addValue("timeLimitMinutes", request.timeLimitMinutes())
                        .addValue("correlationId", correlationId)
                        .addValue("agentTraceId", traceId)
                        .addValue("schemaVersion", request.schemaVersion())
                        .addValue("requestContext", rawRequestJson == null ? "{}" : rawRequestJson));
        insertSources(planId, request, sources);
        insertAgentRequest(planId, userId, request, sources, correlationId);
        return planId;
    }

    @Override
    @Transactional
    public void completeReady(UUID userId, UUID planId, AgentReadyPlanResponse response, String rawResponseJson) {
        lockProcessingPlan(userId, planId);
        insertTasks(planId, response.timeline());
        insertMiseEnPlace(planId, response.miseEnPlace());
        insertDishCompletions(planId, response.dishCompletions());
        insertCompletionItems(planId, response.completionChecklist());
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = 'READY',
                    solver_status = :solverStatus,
                    makespan_minutes = :makespan,
                    response_json = CAST(:responseJson AS jsonb),
                    safety_policy_json = CAST(:safetyPolicyJson AS jsonb),
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId)
                        .addValue("solverStatus", response.solverStatus())
                        .addValue("makespan", response.makespanMinutes())
                        .addValue("responseJson", rawResponseJson, java.sql.Types.VARCHAR)
                        .addValue("safetyPolicyJson", toJsonOrNull(response.safetyPolicy()), java.sql.Types.VARCHAR));
        updateAgentRequest(planId, "READY", response.solverStatus(), response.makespanMinutes(),
                response.timeline().size());
    }

    @Override
    @Transactional
    public void completeConfirmation(
            UUID userId,
            UUID planId,
            AgentConfirmationPlanResponse response,
            String rawResponseJson) {
        lockProcessingPlan(userId, planId);
        insertAssumptions(planId, response.assumptions());
        insertRepairOptions(planId, response.repairOptions());
        insertConfirmationQuestions(planId, response.confirmationQuestions());
        insertDecisions(planId, response.decisions());
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = 'NEEDS_CONFIRMATION',
                    plan_revision = :planRevision,
                    response_json = CAST(:responseJson AS jsonb),
                    safety_policy_json = CAST(:safetyPolicyJson AS jsonb),
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId)
                        .addValue("planRevision", response.planRevision())
                        .addValue("responseJson", rawResponseJson, java.sql.Types.VARCHAR)
                        .addValue("safetyPolicyJson", toJsonOrNull(response.safetyPolicy()), java.sql.Types.VARCHAR));
        updateAgentRequest(planId, "NEEDS_CONFIRMATION", null, null, null);
    }

    @Override
    @Transactional
    public void completeInfeasible(
            UUID userId,
            UUID planId,
            AgentInfeasiblePlanResponse response,
            String rawResponseJson) {
        lockProcessingPlan(userId, planId);
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = 'INFEASIBLE',
                    response_json = CAST(:responseJson AS jsonb),
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId)
                        .addValue("responseJson", rawResponseJson));
        updateAgentRequest(planId, "INFEASIBLE", null, null, null);
    }

    @Override
    @Transactional
    public void completeFailed(
            UUID userId,
            UUID planId,
            CookingAgentFailureCode code,
            AgentFailedPlanResponse response,
            String rawResponseJson) {
        lockProcessingPlan(userId, planId);
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = 'FAILED',
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    response_json = CAST(:responseJson AS jsonb),
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId)
                        .addValue("errorCode", code.name())
                        .addValue("errorMessage", response == null ? null : response.message())
                        .addValue("responseJson", rawResponseJson, java.sql.Types.VARCHAR));
        updateAgentRequest(planId, "FAILED", null, null, null);
    }

    @Override
    public Optional<CookingPlanResult> findOwned(UUID userId, UUID planId) {
        Optional<RootRow> root = jdbcTemplate.query("""
                SELECT id, user_id, status, agent_request_id, plan_revision, region, cooking_date,
                       serving_at, time_limit_minutes, solver_status, makespan_minutes, correlation_id,
                       schema_version, error_code, error_message, request_context, response_json,
                       safety_policy_json, created_at, completed_at
                FROM cooking_plan
                WHERE id = :planId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId),
                this::rootRow)
                .stream()
                .findFirst();
        if (root.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assemble(root.get()));
    }

    @Override
    public Optional<String> findRequestContext(UUID userId, UUID planId) {
        return jdbcTemplate.query("""
                SELECT request_context
                FROM cooking_plan
                WHERE id = :planId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId),
                (rs, rowNum) -> rs.getString("request_context"))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PlanLineage> findLineage(UUID userId, UUID planId) {
        return jdbcTemplate.query("""
                SELECT id, parent_plan_id, root_plan_id
                FROM cooking_plan
                WHERE id = :planId AND user_id = :userId
                """, new MapSqlParameterSource()
                .addValue("planId", planId)
                .addValue("userId", userId),
                (rs, rowNum) -> new PlanLineage(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_plan_id", UUID.class),
                        rs.getObject("root_plan_id", UUID.class)))
                .stream().findFirst();
    }

    @Override
    public List<CookingPlanSummary> findOwnedPage(UUID userId, int page, int size) {
        return jdbcTemplate.query("""
                SELECT cp.id,
                       cp.status,
                       cp.makespan_minutes,
                       count(DISTINCT cps.sequence_no)::int AS source_count,
                       count(DISTINCT cpt.task_id)::int AS task_count,
                       cp.created_at,
                       cp.completed_at
                FROM cooking_plan cp
                LEFT JOIN cooking_plan_source cps ON cps.plan_id = cp.id
                LEFT JOIN cooking_plan_task cpt ON cpt.plan_id = cp.id
                WHERE cp.user_id = :userId
                GROUP BY cp.id
                ORDER BY cp.created_at DESC, cp.id DESC
                LIMIT :limit OFFSET :offset
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("limit", size)
                        .addValue("offset", page * size),
                this::summaryRow);
    }

    @Override
    public long countOwned(UUID userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM cooking_plan
                WHERE user_id = :userId
                """,
                new MapSqlParameterSource("userId", userId),
                Long.class);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public void createGeneration(UUID planId, String taskId) {
        jdbcTemplate.update("""
                INSERT INTO cooking_plan_generation (
                    plan_id, agent_task_id, sync_state, next_poll_at, attempt_count
                )
                VALUES (:planId, :taskId, 'PENDING', CURRENT_TIMESTAMP, 0)
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("taskId", taskId));
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET agent_task_id = :taskId
                WHERE id = :planId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("taskId", taskId));
    }

    @Override
    @Transactional
    public List<GenerationClaim> claimDueGenerations(int batch, Duration pollInterval) {
        return jdbcTemplate.query("""
                UPDATE cooking_plan_generation g
                SET sync_state = 'POLLING',
                    next_poll_at = CURRENT_TIMESTAMP + make_interval(secs => :pollIntervalSeconds),
                    attempt_count = attempt_count + 1
                FROM (
                    SELECT g2.plan_id
                    FROM cooking_plan_generation g2
                    JOIN cooking_plan cp ON cp.id = g2.plan_id
                    WHERE g2.sync_state IN ('PENDING', 'POLLING')
                      AND g2.next_poll_at <= CURRENT_TIMESTAMP
                      AND cp.status = 'PROCESSING'
                    ORDER BY g2.next_poll_at
                    LIMIT :batch
                    FOR UPDATE SKIP LOCKED
                ) q
                JOIN cooking_plan cp2 ON cp2.id = q.plan_id
                WHERE g.plan_id = q.plan_id
                RETURNING g.plan_id, cp2.user_id, g.agent_task_id, g.attempt_count
                """,
                new MapSqlParameterSource()
                        .addValue("batch", batch)
                        .addValue("pollIntervalSeconds", pollInterval.toSeconds()),
                (rs, rowNum) -> new GenerationClaim(
                        rs.getObject("plan_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("agent_task_id"),
                        rs.getInt("attempt_count")));
    }

    @Override
    @Transactional
    public void updateGenerationProgress(
            UUID planId,
            String node,
            int completedSteps,
            String message,
            Duration nextDelay) {
        jdbcTemplate.update("""
                UPDATE cooking_plan_generation
                SET sync_state = 'PENDING',
                    last_progress_node = :node,
                    last_progress_steps = :steps,
                    last_progress_message = :message,
                    next_poll_at = CURRENT_TIMESTAMP + make_interval(secs => :delaySeconds)
                WHERE plan_id = :planId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("node", node)
                        .addValue("steps", completedSteps)
                        .addValue("message", message)
                        .addValue("delaySeconds", nextDelay.toSeconds()));
    }

    @Override
    @Transactional
    public void completeGeneration(UUID planId, String syncState) {
        jdbcTemplate.update("""
                UPDATE cooking_plan_generation
                SET sync_state = :syncState,
                    next_poll_at = CURRENT_TIMESTAMP
                WHERE plan_id = :planId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("syncState", syncState));
    }

    @Override
    public Optional<GenerationRow> findGeneration(UUID planId) {
        return jdbcTemplate.query("""
                SELECT plan_id, agent_task_id, sync_state, next_poll_at, attempt_count,
                       last_error_code, last_progress_node, last_progress_steps, last_progress_message
                FROM cooking_plan_generation
                WHERE plan_id = :planId
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new GenerationRow(
                        rs.getObject("plan_id", UUID.class),
                        rs.getString("agent_task_id"),
                        rs.getString("sync_state"),
                        rs.getObject("next_poll_at", OffsetDateTime.class),
                        rs.getInt("attempt_count"),
                        rs.getString("last_error_code"),
                        rs.getString("last_progress_node"),
                        rs.getInt("last_progress_steps"),
                        rs.getString("last_progress_message")))
                .stream()
                .findFirst();
    }

    // =========================================================================
    // Writes
    // =========================================================================

    private void insertSources(UUID planId, AgentGeneratePlanRequest request, List<AgentRecipeInput> sources) {
        int sequence = 1;
        for (AgentRecipeInput source : sources) {
            UUID sourceId = uuidOrNull(source.recipeId());
            String sourceType = isOwnerRecipe(sourceId) ? "OWNER" : "CATALOGUE";
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_source (
                        plan_id, sequence_no, source_type, source_id, target_servings, dish_name, recipe_text
                    )
                    VALUES (:planId, :sequenceNo, :sourceType, :sourceId, :targetServings, :dishName, :recipeText)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("sourceType", sourceType)
                            .addValue("sourceId", sourceId)
                            .addValue("targetServings", source.targetServings())
                            .addValue("dishName", firstLine(source.text()))
                            .addValue("recipeText", source.text()));
        }
    }

    private boolean isOwnerRecipe(UUID sourceId) {
        if (sourceId == null) {
            return false;
        }
        Boolean owned = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM user_recipe
                    WHERE id = :sourceId AND deleted_at IS NULL
                )
                """,
                new MapSqlParameterSource("sourceId", sourceId),
                Boolean.class);
        return Boolean.TRUE.equals(owned);
    }

    private void insertAgentRequest(
            UUID planId,
            UUID userId,
            AgentGeneratePlanRequest request,
            List<AgentRecipeInput> sources,
            String correlationId) {
        jdbcTemplate.update("""
                INSERT INTO agent_request (
                    id, request_id, user_id, plan_id, correlation_id, schema_version, recipe_count, status
                )
                VALUES (:id, :requestId, :userId, :planId, :correlationId, :schemaVersion, :recipeCount, 'PROCESSING')
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("requestId", request.requestId())
                        .addValue("userId", userId)
                        .addValue("planId", planId)
                        .addValue("correlationId", correlationId)
                        .addValue("schemaVersion", request.schemaVersion())
                        .addValue("recipeCount", sources.size()));
    }

    private void updateAgentRequest(UUID planId, String status, String solverStatus, Integer makespan, Integer taskCount) {
        jdbcTemplate.update("""
                UPDATE agent_request
                SET status = :status,
                    solver_status = :solverStatus,
                    makespan_minutes = :makespan,
                    task_count = :taskCount
                WHERE plan_id = :planId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("status", status)
                        .addValue("solverStatus", solverStatus)
                        .addValue("makespan", makespan)
                        .addValue("taskCount", taskCount));
    }

    private void insertTasks(UUID planId, List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentTimelineTask> timeline) {
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentTimelineTask task : timeline) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_task (
                        plan_id, task_id, dish_id, instruction, duration_minutes, work_mode, category,
                        heat_level, target_temperature_c, start_minute, end_minute, resources
                    )
                    VALUES (
                        :planId, :taskId, :dishId, :instruction, :duration, :workMode, :category,
                        :heatLevel, :targetTemperatureC, :startMinute, :endMinute, CAST(:resources AS text[])
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("taskId", task.taskId())
                            .addValue("dishId", task.dishId())
                            .addValue("instruction", task.instruction())
                            .addValue("duration", task.durationMinutes())
                            .addValue("workMode", task.workMode())
                            .addValue("category", task.category())
                            .addValue("heatLevel", task.heatLevel() == null ? "NONE" : task.heatLevel())
                            .addValue("targetTemperatureC", null)
                            .addValue("startMinute", task.startMinute())
                            .addValue("endMinute", task.endMinute())
                            .addValue("resources", task.resources().toArray(String[]::new)));
        }
    }

    private void insertMiseEnPlace(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentMiseEnPlaceItem> items) {
        int sequence = 1;
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentMiseEnPlaceItem item : items) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_mise_en_place (
                        plan_id, sequence_no, instruction, ingredient, operation, duration_minutes,
                        resources, when_needed
                    )
                    VALUES (:planId, :sequenceNo, :instruction, :ingredient, :operation, :duration,
                            CAST(:resources AS text[]), :whenNeeded)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("instruction", item.instruction())
                            .addValue("ingredient", truncate(item.ingredient(), 256))
                            .addValue("operation", truncate(item.operation(), 64))
                            .addValue("duration", item.durationMinutes())
                            .addValue("resources", item.resources().toArray(String[]::new))
                            .addValue("whenNeeded", truncate(item.whenNeeded(), 64)));
        }
    }

    private void insertDishCompletions(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentDishCompletion> completions) {
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentDishCompletion completion : completions) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_dish_completion (
                        plan_id, dish_id, completion_minute, task_count, is_shared
                    )
                    VALUES (:planId, :dishId, :completionMinute, :taskCount, :isShared)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("dishId", completion.dishId())
                            .addValue("completionMinute", completion.completionMinute())
                            .addValue("taskCount", completion.taskCount())
                            .addValue("isShared", completion.isShared()));
        }
    }

    private void insertCompletionItems(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentCompletionItem> items) {
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentCompletionItem item : items) {
            UUID completionItemId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_completion_item (
                        id, plan_id, completion_item_id, ingredient_name, recipe_ids
                    )
                    VALUES (:id, :planId, :completionItemId, :ingredientName, CAST(:recipeIds AS text[]))
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", completionItemId)
                            .addValue("planId", planId)
                            .addValue("completionItemId", item.completionItemId())
                            .addValue("ingredientName", item.ingredientName())
                            .addValue("recipeIds", item.recipeIds().toArray(String[]::new)));
            for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentLotAllocation allocation : item.allocations()) {
                UUID inventoryLotId = uuidOrNull(allocation.inventoryLotId());
                if (inventoryLotId == null || !lotExists(inventoryLotId)) {
                    // The allocation references a transient lot injected by the
                    // agent for a "buy missing ingredients" decision — it is not
                    // persisted in inventory_lot, so the FK cannot be satisfied.
                    // Keep the completion item; skip only the allocation row.
                    continue;
                }
                jdbcTemplate.update("""
                        INSERT INTO cooking_plan_lot_allocation (
                            id, completion_item_id, inventory_lot_id, quantity, unit, is_reserved
                        )
                        VALUES (:id, :completionItemId, :inventoryLotId, :quantity, :unit, false)
                        """,
                        new MapSqlParameterSource()
                                .addValue("id", UUID.randomUUID())
                                .addValue("completionItemId", completionItemId)
                                .addValue("inventoryLotId", inventoryLotId)
                                .addValue("quantity", allocation.quantity())
                                .addValue("unit", allocation.unit()));
            }
        }
    }

    private void insertAssumptions(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentAssumption> assumptions) {
        int sequence = 1;
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentAssumption assumption : assumptions) {
            String sourceType = assumption.evidence().isEmpty() ? "LLM_guess" : assumption.evidence().get(0).sourceType();
            String evidenceUrl = assumption.evidence().isEmpty() ? null : assumption.evidence().get(0).url();
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_assumption (
                        plan_id, sequence_no, text, confidence, source_type, evidence_url
                    )
                    VALUES (:planId, :sequenceNo, :text, :confidence, :sourceType, :evidenceUrl)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("text", assumption.text())
                            .addValue("confidence", assumption.confidence())
                            .addValue("sourceType", sourceType)
                            .addValue("evidenceUrl", evidenceUrl));
        }
    }

    private void insertRepairOptions(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentRepairOption> options) {
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentRepairOption option : options) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_repair_option (
                        plan_id, option_id, option_type, description, changes, effects, revalidation_status
                    )
                    VALUES (:planId, :optionId, :optionType, :description, CAST(:changes AS text[]),
                            CAST(:effects AS text[]), :revalidationStatus)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("optionId", option.optionId())
                            .addValue("optionType", option.optionType())
                            .addValue("description", option.description())
                            .addValue("changes", option.changes().toArray(String[]::new))
                            .addValue("effects", option.effects().toArray(String[]::new))
                            .addValue("revalidationStatus", option.revalidationStatus()));
        }
    }

    private void insertConfirmationQuestions(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion> questions) {
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion question : questions) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_confirmation_question (
                        plan_id, question_id, field_path, prompt, response_type, options, required, suggested_value
                    )
                    VALUES (:planId, :questionId, :fieldPath, :prompt, :responseType,
                            CAST(:options AS jsonb), :required, :suggestedValue)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("questionId", question.questionId())
                            .addValue("fieldPath", question.fieldPath())
                            .addValue("prompt", question.prompt())
                            .addValue("responseType", question.responseType())
                            .addValue("options", toJson(question.options()))
                            .addValue("required", question.required())
                            .addValue("suggestedValue", question.suggestedValue()));
        }
    }

    private void insertDecisions(
            UUID planId,
            List<com.foodmind.foodmindbackend.cooking.domain.agent.AgentDecision> decisions) {
        int sequence = 1;
        for (com.foodmind.foodmindbackend.cooking.domain.agent.AgentDecision decision : decisions) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_decision (
                        plan_id, sequence_no, option_id, option_type, payload, plan_revision
                    )
                    VALUES (:planId, :sequenceNo, :optionId, :optionType, CAST(:payload AS jsonb), :planRevision)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("optionId", decision.optionId())
                            .addValue("optionType", decision.optionType())
                            .addValue("payload", toJson(decision.payload()))
                            .addValue("planRevision", decision.planRevision()));
        }
    }

    private void lockProcessingPlan(UUID userId, UUID planId) {
        List<UUID> locked = jdbcTemplate.query("""
                SELECT id
                FROM cooking_plan
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                FOR UPDATE
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (locked.isEmpty()) {
            throw new IllegalStateException("Cooking plan is not completable.");
        }
    }

    // =========================================================================
    // Reads
    // =========================================================================

    private CookingPlanResult assemble(RootRow root) {
        List<CookingPlanResult.Source> sources = sources(root.id());
        // Failed and in-flight plans may retain a non-conforming raw agent payload
        // for diagnostics. Only terminal business responses are safe to deserialize.
        AgentPlanResponse parsed = switch (root.status()) {
            case "READY", "NEEDS_CONFIRMATION", "INFEASIBLE" -> parseResponse(root.responseJson());
            default -> null;
        };
        CookingPlanResult.SafetyPolicy safetyPolicy = safetyPolicy(root.safetyPolicyJson());
        return switch (root.status()) {
            case "READY" -> ready(root, sources, parsed, safetyPolicy);
            case "NEEDS_CONFIRMATION" -> confirmation(root, sources, parsed, safetyPolicy);
            case "INFEASIBLE" -> infeasible(root, sources, parsed);
            case "FAILED" -> failed(root, sources);
            default -> processing(root, sources);
        };
    }

    private CookingPlanResult ready(
            RootRow root,
            List<CookingPlanResult.Source> sources,
            AgentPlanResponse parsed,
            CookingPlanResult.SafetyPolicy safetyPolicy) {
        String explanation = parsed instanceof com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse ready
                ? ready.explanation()
                : null;
        String explanationSource = parsed instanceof com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse ready
                ? ready.explanationSource()
                : null;
        return new CookingPlanResult(
                root.id(), root.status(), root.planRevision(), root.region(), root.cookingDate(),
                root.servingAt(), root.timeLimitMinutes(), root.solverStatus(), root.makespanMinutes(),
                root.correlationId(), root.schemaVersion(), root.errorCode(), root.errorMessage(),
                root.createdAt(), root.completedAt(),
                sources,
                timeline(root.id()),
                miseEnPlace(root.id()),
                dishCompletions(root.id()),
                completionItems(root.id()),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                safetyPolicy, explanation, explanationSource);
    }

    private CookingPlanResult confirmation(
            RootRow root,
            List<CookingPlanResult.Source> sources,
            AgentPlanResponse parsed,
            CookingPlanResult.SafetyPolicy safetyPolicy) {
        List<String> questions = parsed instanceof com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse confirmation
                ? confirmation.questions()
                : List.of();
        return new CookingPlanResult(
                root.id(), root.status(), root.planRevision(), root.region(), root.cookingDate(),
                root.servingAt(), root.timeLimitMinutes(), root.solverStatus(), root.makespanMinutes(),
                root.correlationId(), root.schemaVersion(), root.errorCode(), root.errorMessage(),
                root.createdAt(), root.completedAt(),
                sources,
                List.of(), List.of(), List.of(), List.of(),
                assumptions(root.id()),
                repairOptions(root.id()),
                questions,
                confirmationQuestions(root.id()),
                decisions(root.id()),
                List.of(), List.of(),
                safetyPolicy, null, null);
    }

    private CookingPlanResult infeasible(
            RootRow root,
            List<CookingPlanResult.Source> sources,
            AgentPlanResponse parsed) {
        List<String> reasons = List.of();
        List<String> safeAlternatives = List.of();
        if (parsed instanceof AgentInfeasiblePlanResponse infeasible) {
            reasons = infeasible.reasons();
            safeAlternatives = infeasible.safeAlternatives();
        }
        return new CookingPlanResult(
                root.id(), root.status(), root.planRevision(), root.region(), root.cookingDate(),
                root.servingAt(), root.timeLimitMinutes(), root.solverStatus(), root.makespanMinutes(),
                root.correlationId(), root.schemaVersion(), root.errorCode(), root.errorMessage(),
                root.createdAt(), root.completedAt(),
                sources,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                reasons, safeAlternatives,
                null, null, null);
    }

    private CookingPlanResult failed(RootRow root, List<CookingPlanResult.Source> sources) {
        return new CookingPlanResult(
                root.id(), root.status(), root.planRevision(), root.region(), root.cookingDate(),
                root.servingAt(), root.timeLimitMinutes(), root.solverStatus(), root.makespanMinutes(),
                root.correlationId(), root.schemaVersion(), root.errorCode(), root.errorMessage(),
                root.createdAt(), root.completedAt(),
                sources,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                null, null, null);
    }

    private CookingPlanResult processing(RootRow root, List<CookingPlanResult.Source> sources) {
        return new CookingPlanResult(
                root.id(), root.status(), root.planRevision(), root.region(), root.cookingDate(),
                root.servingAt(), root.timeLimitMinutes(), root.solverStatus(), root.makespanMinutes(),
                root.correlationId(), root.schemaVersion(), root.errorCode(), root.errorMessage(),
                root.createdAt(), root.completedAt(),
                sources,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                null, null, null);
    }

    private List<CookingPlanResult.Source> sources(UUID planId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, source_type, source_id, target_servings, dish_name, recipe_text
                FROM cooking_plan_source
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.Source(
                        rs.getInt("sequence_no"),
                        rs.getString("source_type"),
                        rs.getObject("source_id", UUID.class),
                        rs.getBigDecimal("target_servings"),
                        rs.getString("dish_name"),
                        rs.getString("recipe_text")));
    }

    private List<CookingPlanResult.TimelineTask> timeline(UUID planId) {
        return jdbcTemplate.query("""
                SELECT task_id, dish_id, instruction, duration_minutes, work_mode, category, heat_level,
                       target_temperature_c, start_minute, end_minute, resources
                FROM cooking_plan_task
                WHERE plan_id = :planId
                ORDER BY start_minute, task_id
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.TimelineTask(
                        rs.getString("task_id"),
                        rs.getString("dish_id"),
                        rs.getString("instruction"),
                        rs.getInt("duration_minutes"),
                        rs.getString("work_mode"),
                        rs.getString("category"),
                        rs.getString("heat_level"),
                        rs.getBigDecimal("target_temperature_c"),
                        nullableInt(rs, "start_minute"),
                        nullableInt(rs, "end_minute"),
                        stringArray(rs.getArray("resources"))));
    }

    private List<CookingPlanResult.MiseEnPlaceItem> miseEnPlace(UUID planId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, instruction, ingredient, operation, duration_minutes, resources, when_needed
                FROM cooking_plan_mise_en_place
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.MiseEnPlaceItem(
                        rs.getInt("sequence_no"),
                        rs.getString("instruction"),
                        rs.getString("ingredient"),
                        rs.getString("operation"),
                        nullableInt(rs, "duration_minutes"),
                        stringArray(rs.getArray("resources")),
                        rs.getString("when_needed")));
    }

    private List<CookingPlanResult.DishCompletion> dishCompletions(UUID planId) {
        return jdbcTemplate.query("""
                SELECT dish_id, completion_minute, task_count, is_shared
                FROM cooking_plan_dish_completion
                WHERE plan_id = :planId
                ORDER BY completion_minute, dish_id
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.DishCompletion(
                        rs.getString("dish_id"),
                        rs.getInt("completion_minute"),
                        rs.getInt("task_count"),
                        rs.getBoolean("is_shared")));
    }

    private List<CookingPlanResult.CompletionItem> completionItems(UUID planId) {
        List<CookingPlanResult.CompletionItem> items = jdbcTemplate.query("""
                SELECT id, completion_item_id, ingredient_name, recipe_ids
                FROM cooking_plan_completion_item
                WHERE plan_id = :planId
                ORDER BY completion_item_id
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.CompletionItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("completion_item_id"),
                        rs.getString("ingredient_name"),
                        stringArray(rs.getArray("recipe_ids")),
                        new ArrayList<>()));
        List<Map.Entry<UUID, CookingPlanResult.LotAllocation>> allocations = jdbcTemplate.query("""
                SELECT al.id, al.completion_item_id, al.inventory_lot_id, al.quantity, al.unit, al.is_reserved
                FROM cooking_plan_lot_allocation al
                JOIN cooking_plan_completion_item ci ON ci.id = al.completion_item_id
                WHERE ci.plan_id = :planId
                ORDER BY al.completion_item_id, al.id
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> Map.entry(
                        rs.getObject("completion_item_id", UUID.class),
                        new CookingPlanResult.LotAllocation(
                                rs.getObject("id", UUID.class),
                                rs.getObject("completion_item_id", UUID.class),
                                rs.getObject("inventory_lot_id", UUID.class),
                                rs.getBigDecimal("quantity"),
                                rs.getString("unit"),
                                rs.getBoolean("is_reserved"))));
        Map<UUID, List<CookingPlanResult.LotAllocation>> byItem = new LinkedHashMap<>();
        for (Map.Entry<UUID, CookingPlanResult.LotAllocation> entry : allocations) {
            byItem.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue());
        }
        for (int index = 0; index < items.size(); index++) {
            CookingPlanResult.CompletionItem item = items.get(index);
            items.set(index, withAllocations(item, byItem.getOrDefault(item.id(), List.of())));
        }
        return items;
    }

    private CookingPlanResult.CompletionItem withAllocations(
            CookingPlanResult.CompletionItem item,
            List<CookingPlanResult.LotAllocation> allocations) {
        return new CookingPlanResult.CompletionItem(
                item.id(), item.completionItemId(), item.ingredientName(), item.recipeIds(), allocations);
    }

    private List<CookingPlanResult.Assumption> assumptions(UUID planId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, text, confidence, source_type, evidence_url
                FROM cooking_plan_assumption
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.Assumption(
                        rs.getInt("sequence_no"),
                        rs.getString("text"),
                        rs.getBigDecimal("confidence"),
                        rs.getString("source_type"),
                        rs.getString("evidence_url")));
    }

    private List<CookingPlanResult.RepairOption> repairOptions(UUID planId) {
        return jdbcTemplate.query("""
                SELECT option_id, option_type, description, changes, effects, revalidation_status
                FROM cooking_plan_repair_option
                WHERE plan_id = :planId
                ORDER BY option_id
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.RepairOption(
                        rs.getString("option_id"),
                        rs.getString("option_type"),
                        rs.getString("description"),
                        stringArray(rs.getArray("changes")),
                        stringArray(rs.getArray("effects")),
                        rs.getString("revalidation_status")));
    }

    private List<CookingPlanResult.ConfirmationQuestion> confirmationQuestions(UUID planId) {
        return jdbcTemplate.query("""
                SELECT question_id, field_path, prompt, response_type, options, required, suggested_value
                FROM cooking_plan_confirmation_question
                WHERE plan_id = :planId
                ORDER BY question_id
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.ConfirmationQuestion(
                        rs.getString("question_id"),
                        rs.getString("field_path"),
                        rs.getString("prompt"),
                        rs.getString("response_type"),
                        parseQuestionOptions(rs.getString("options")),
                        rs.getBoolean("required"),
                        rs.getString("suggested_value")));
    }

    private List<CookingPlanResult.QuestionOption> parseQuestionOptions(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CookingPlanResult.QuestionOption>>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid confirmation question options JSON.", exception);
        }
    }

    private List<CookingPlanResult.Decision> decisions(UUID planId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, option_id, option_type, payload, plan_revision
                FROM cooking_plan_decision
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanResult.Decision(
                        rs.getInt("sequence_no"),
                        rs.getString("option_id"),
                        rs.getString("option_type"),
                        parseObjectMap(rs.getString("payload")),
                        rs.getString("plan_revision")));
    }

    private Map<String, Object> parseObjectMap(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid decision payload JSON.", exception);
        }
    }

    private CookingPlanResult.SafetyPolicy safetyPolicy(String json) {
        if (json == null) {
            return null;
        }
        try {
            AgentSafetyPolicy policy = objectMapper.readValue(json, AgentSafetyPolicy.class);
            return resultMapper.safetyPolicy(policy);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid safety policy JSON.", exception);
        }
    }

    private AgentPlanResponse parseResponse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentPlanResponse.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid stored agent response JSON.", exception);
        }
    }

    private RootRow rootRow(ResultSet rs, int rowNum) throws SQLException {
        return new RootRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getString("agent_request_id"),
                rs.getString("plan_revision"),
                rs.getString("region"),
                rs.getObject("cooking_date", LocalDate.class),
                rs.getObject("serving_at", OffsetDateTime.class),
                nullableInt(rs, "time_limit_minutes"),
                rs.getString("solver_status"),
                nullableInt(rs, "makespan_minutes"),
                rs.getString("correlation_id"),
                rs.getString("schema_version"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getString("request_context"),
                rs.getString("response_json"),
                rs.getString("safety_policy_json"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private CookingPlanSummary summaryRow(ResultSet rs, int rowNum) throws SQLException {
        return new CookingPlanSummary(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getInt("source_count"),
                rs.getInt("task_count"),
                nullableInt(rs, "makespan_minutes"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static List<String> stringArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String.class::cast).toList();
    }

    private static UUID uuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return null;
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    /** Truncates a value to the column's varchar limit, avoiding DB constraint errors. */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** True when the inventory lot exists in the persisted inventory_lot table. */
    private boolean lotExists(UUID lotId) {
        List<UUID> rows = jdbcTemplate.query(
                "SELECT id FROM inventory_lot WHERE id = :lotId",
                new MapSqlParameterSource("lotId", lotId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        return !rows.isEmpty();
    }

    private String sanitiseCorrelationId(String traceId) {
        if (traceId == null) {
            return UUID.randomUUID().toString();
        }
        String cleaned = traceId.replaceAll("[^A-Za-z0-9_-]", "-");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise cooking persistence payload.", exception);
        }
    }

    private String toJsonOrNull(Object value) {
        return value == null ? null : toJson(value);
    }

    private record RootRow(
            UUID id,
            String status,
            String agentRequestId,
            String planRevision,
            String region,
            LocalDate cookingDate,
            OffsetDateTime servingAt,
            Integer timeLimitMinutes,
            String solverStatus,
            Integer makespanMinutes,
            String correlationId,
            String schemaVersion,
            String errorCode,
            String errorMessage,
            String requestContext,
            String responseJson,
            String safetyPolicyJson,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt) {
    }
}
