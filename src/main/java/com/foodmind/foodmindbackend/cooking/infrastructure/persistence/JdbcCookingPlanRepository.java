package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingInputSource;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanIngredient;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanInput;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanStep;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanWarning;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.RecipeIngredientSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.RecipeStepSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.ValidatedCookingAgentResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Repository
public class JdbcCookingPlanRepository implements CookingPlanRepository {

    private static final String AGENT_CONTRACT_VERSION = "cooking-agent-v1";
    private static final String FALLBACK_VERSION = "cooking-fallback-v1";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCookingPlanRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public CookingAgentCommand createProcessingPlan(
            UUID userId,
            CookingPlanRequestContext request,
            Map<String, Object> requestSnapshot,
            Map<String, Object> preferenceSnapshot,
            List<RecipeCandidate> candidates,
            String traceId,
            UUID correlationId) {
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cooking_plan (
                    id, user_id, status, servings, max_minutes, max_budget, currency, request_context, correlation_id
                )
                VALUES (
                    :id, :userId, 'CREATED', :servings, :maxMinutes, :maxBudget, :currency,
                    CAST(:requestContext AS jsonb), :correlationId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", planId)
                        .addValue("userId", userId)
                        .addValue("servings", request.servings())
                        .addValue("maxMinutes", request.maxMinutes())
                        .addValue("maxBudget", request.maxBudget())
                        .addValue("currency", request.currency())
                        .addValue("requestContext", toJson(requestSnapshot))
                        .addValue("correlationId", correlationId));
        insertInputs(planId, request.ingredients());
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = 'PROCESSING'
                WHERE id = :planId
                  AND status = 'CREATED'
                """,
                new MapSqlParameterSource("planId", planId));
        return new CookingAgentCommand(
                AGENT_CONTRACT_VERSION,
                UUID.randomUUID(),
                planId,
                traceId,
                OffsetDateTime.now().plusSeconds(3),
                requestSnapshot,
                preferenceSnapshot,
                candidates.stream().map(candidate -> new CookingAgentCandidate(
                        candidate.recipeId(),
                        candidate,
                        candidateSnapshot(candidate))).toList());
    }

    @Override
    public void completePlan(UUID userId, UUID planId, ValidatedCookingAgentResult result) {
        lockProcessingPlan(userId, planId);
        if (result.ingredients().isEmpty() || result.steps().isEmpty()) {
            throw new IllegalStateException("Successful cooking plans require at least one ingredient and step.");
        }
        for (CookingPlanIngredient ingredient : result.ingredients()) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_ingredient (
                        plan_id, sequence_no, ingredient_name, quantity, unit, availability
                    )
                    VALUES (:planId, :sequenceNo, :ingredientName, :quantity, :unit, :availability)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", ingredient.sequenceNo())
                            .addValue("ingredientName", ingredient.ingredientName())
                            .addValue("quantity", ingredient.quantity())
                            .addValue("unit", ingredient.unit())
                            .addValue("availability", ingredient.availability()));
        }
        for (CookingPlanStep step : result.steps()) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_step (plan_id, step_no, instruction)
                    VALUES (:planId, :stepNo, :instruction)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("stepNo", step.stepNo())
                            .addValue("instruction", step.instruction()));
        }
        for (CookingPlanWarning warning : result.warnings()) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_warning (plan_id, sequence_no, warning_code, message)
                    VALUES (:planId, :sequenceNo, :warningCode, :message)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", warning.sequenceNo())
                            .addValue("warningCode", warning.warningCode())
                            .addValue("message", warning.message()));
        }
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = 'SUCCEEDED',
                    source_recipe_id = :sourceRecipeId,
                    agent_contract_version = :agentContractVersion,
                    fallback_status = 'NOT_REQUIRED',
                    agent_trace_id = :agentTraceId,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId)
                        .addValue("sourceRecipeId", result.sourceRecipeId())
                        .addValue("agentContractVersion", result.agentContractVersion())
                        .addValue("agentTraceId", result.agentTraceId()));
    }

    @Override
    public void markFailed(
            UUID userId,
            UUID planId,
            CookingAgentFailureCode failureCode,
            String agentContractVersion,
            String agentTraceId) {
        lockProcessingPlan(userId, planId);
        boolean noRecipe = failureCode == CookingAgentFailureCode.NO_RECIPE_MATCH;
        String safeTraceId = agentContractVersion == null ? null : agentTraceId;
        jdbcTemplate.update("""
                UPDATE cooking_plan
                SET status = :status,
                    agent_contract_version = :agentContractVersion,
                    fallback_status = :fallbackStatus,
                    fallback_version = :fallbackVersion,
                    agent_trace_id = :agentTraceId,
                    failure_code = :failureCode,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :planId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId)
                        .addValue("status", noRecipe ? "NO_VALID_RECIPE" : "FAILED")
                        .addValue("agentContractVersion", agentContractVersion)
                        .addValue("fallbackStatus", noRecipe ? "NO_VALID_RECIPE" : "FAILED")
                        .addValue("fallbackVersion", FALLBACK_VERSION)
                        .addValue("agentTraceId", safeTraceId)
                        .addValue("failureCode", failureCode.name()));
    }

    @Override
    public Optional<CookingPlanResult> findOwned(UUID userId, UUID planId, String traceId) {
        Optional<PlanRow> plan = jdbcTemplate.query("""
                SELECT id, status, source_recipe_id, agent_contract_version, fallback_status, fallback_version,
                       failure_code, created_at, completed_at
                FROM cooking_plan
                WHERE id = :planId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("userId", userId),
                this::planRow)
                .stream()
                .findFirst();
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CookingPlanResult(
                plan.get().id(),
                traceId,
                plan.get().status(),
                plan.get().sourceRecipeId(),
                plan.get().agentContractVersion(),
                plan.get().fallbackStatus(),
                plan.get().fallbackVersion(),
                plan.get().failureCode(),
                plan.get().createdAt(),
                plan.get().completedAt(),
                inputs(planId),
                ingredients(planId),
                steps(planId),
                warnings(planId)));
    }

    @Override
    public List<CookingPlanSummary> findOwnedPage(UUID userId, int page, int size) {
        return jdbcTemplate.query("""
                SELECT cp.id,
                       cp.status,
                       cp.source_recipe_id,
                       count(DISTINCT cpi.sequence_no)::int AS input_count,
                       count(DISTINCT cps.step_no)::int AS step_count,
                       cp.created_at,
                       cp.completed_at
                FROM cooking_plan cp
                LEFT JOIN cooking_plan_input cpi ON cpi.plan_id = cp.id
                LEFT JOIN cooking_plan_step cps ON cps.plan_id = cp.id
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

    private void insertInputs(UUID planId, List<CookingPlanInput> inputs) {
        int sequence = 1;
        for (CookingPlanInput input : inputs) {
            jdbcTemplate.update("""
                    INSERT INTO cooking_plan_input (
                        plan_id, sequence_no, ingredient_name, quantity, unit, source
                    )
                    VALUES (:planId, :sequenceNo, :ingredientName, :quantity, :unit, :source)
                    """,
                    new MapSqlParameterSource()
                            .addValue("planId", planId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("ingredientName", input.ingredientName())
                            .addValue("quantity", input.quantity())
                            .addValue("unit", input.unit())
                            .addValue("source", input.source().name()));
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

    private List<CookingPlanInput> inputs(UUID planId) {
        return jdbcTemplate.query("""
                SELECT ingredient_name, quantity, unit, source
                FROM cooking_plan_input
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanInput(
                        rs.getString("ingredient_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit"),
                        CookingInputSource.valueOf(rs.getString("source"))));
    }

    private List<CookingPlanIngredient> ingredients(UUID planId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, ingredient_name, quantity, unit, availability
                FROM cooking_plan_ingredient
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanIngredient(
                        rs.getInt("sequence_no"),
                        rs.getString("ingredient_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit"),
                        rs.getString("availability")));
    }

    private List<CookingPlanStep> steps(UUID planId) {
        return jdbcTemplate.query("""
                SELECT step_no, instruction
                FROM cooking_plan_step
                WHERE plan_id = :planId
                ORDER BY step_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanStep(rs.getInt("step_no"), rs.getString("instruction")));
    }

    private List<CookingPlanWarning> warnings(UUID planId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, warning_code, message
                FROM cooking_plan_warning
                WHERE plan_id = :planId
                ORDER BY sequence_no
                """,
                new MapSqlParameterSource("planId", planId),
                (rs, rowNum) -> new CookingPlanWarning(
                        rs.getInt("sequence_no"),
                        rs.getString("warning_code"),
                        rs.getString("message")));
    }

    private Map<String, Object> candidateSnapshot(RecipeCandidate candidate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recipeId", candidate.recipeId());
        snapshot.put("name", candidate.name());
        snapshot.put("description", candidate.description());
        snapshot.put("defaultServings", candidate.defaultServings());
        snapshot.put("totalMinutes", candidate.totalMinutes());
        snapshot.put("estimatedCost", candidate.estimatedCost());
        snapshot.put("currency", candidate.currency());
        snapshot.put("dietaryTagCodes", candidate.dietaryTagCodes());
        snapshot.put("allergenCodes", candidate.allergenCodes());
        snapshot.put("ingredients", candidate.ingredients().stream().map(this::ingredientSnapshot).toList());
        snapshot.put("steps", candidate.steps().stream().map(this::stepSnapshot).toList());
        return snapshot;
    }

    private Map<String, Object> ingredientSnapshot(RecipeIngredientSnapshot ingredient) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sequenceNo", ingredient.sequenceNo());
        snapshot.put("ingredientName", ingredient.ingredientName());
        snapshot.put("quantity", ingredient.quantity());
        snapshot.put("unit", ingredient.unit());
        snapshot.put("optional", ingredient.optional());
        return snapshot;
    }

    private Map<String, Object> stepSnapshot(RecipeStepSnapshot step) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stepNo", step.stepNo());
        snapshot.put("instruction", step.instruction());
        return snapshot;
    }

    private PlanRow planRow(ResultSet rs, int rowNum) throws SQLException {
        return new PlanRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getObject("source_recipe_id", UUID.class),
                rs.getString("agent_contract_version"),
                rs.getString("fallback_status"),
                rs.getString("fallback_version"),
                rs.getString("failure_code"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private CookingPlanSummary summaryRow(ResultSet rs, int rowNum) throws SQLException {
        return new CookingPlanSummary(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getObject("source_recipe_id", UUID.class),
                rs.getInt("input_count"),
                rs.getInt("step_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise cooking persistence payload.", exception);
        }
    }

    private record PlanRow(
            UUID id,
            String status,
            UUID sourceRecipeId,
            String agentContractVersion,
            String fallbackStatus,
            String fallbackVersion,
            String failureCode,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt) {
    }
}
