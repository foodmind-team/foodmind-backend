package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import com.foodmind.foodmindbackend.cooking.application.port.CookingContextQuery;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanInput;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.RecipeIngredientSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.RecipeStepSnapshot;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Repository
public class JdbcCookingContextQuery implements CookingContextQuery {

    private static final int MAX_CANDIDATES = 25;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCookingContextQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CookingPreferenceRules preferenceRules(UUID userId) {
        return new CookingPreferenceRules(dietaryTagCodes(userId), allergenCodes(userId));
    }

    @Override
    public List<RecipeCandidate> controlledCandidates(
            UUID userId,
            CookingPlanRequestContext request,
            CookingPreferenceRules mergedRules) {
        List<RecipeHeader> headers = jdbcTemplate.query("""
                SELECT r.id,
                       r.name,
                       r.description,
                       r.default_servings,
                       r.prep_minutes + r.cook_minutes AS total_minutes,
                       r.estimated_cost,
                       r.currency,
                       COALESCE(dietary.codes, ARRAY[]::varchar[]) AS dietary_codes,
                       COALESCE(allergen.codes, ARRAY[]::varchar[]) AS allergen_codes
                FROM recipe r
                LEFT JOIN LATERAL (
                    SELECT array_agg(dt.code ORDER BY dt.code) AS codes
                    FROM recipe_dietary_tag rdt
                    JOIN dietary_tag dt ON dt.id = rdt.dietary_tag_id
                    WHERE rdt.recipe_id = r.id
                ) dietary ON true
                LEFT JOIN LATERAL (
                    SELECT array_agg(a.code ORDER BY a.code) AS codes
                    FROM recipe_allergen ra
                    JOIN allergen a ON a.id = ra.allergen_id
                    WHERE ra.recipe_id = r.id
                ) allergen ON true
                WHERE r.curation_status = 'ACTIVE'
                ORDER BY r.estimated_cost NULLS LAST, r.prep_minutes + r.cook_minutes, r.id
                LIMIT :limit
                """,
                new MapSqlParameterSource("limit", MAX_CANDIDATES),
                this::headerRow);
        Map<UUID, List<RecipeIngredientSnapshot>> ingredients = ingredients(headers);
        Map<UUID, List<RecipeStepSnapshot>> steps = steps(headers);
        return headers.stream()
                .map(header -> header.toCandidate(
                        ingredients.getOrDefault(header.id(), List.of()),
                        steps.getOrDefault(header.id(), List.of())))
                .filter(candidate -> compatible(candidate, request, mergedRules))
                .toList();
    }

    private boolean compatible(RecipeCandidate candidate, CookingPlanRequestContext request, CookingPreferenceRules rules) {
        if (request.maxMinutes() != null && candidate.totalMinutes() > request.maxMinutes()) {
            return false;
        }
        if (request.maxBudget() != null) {
            if (candidate.estimatedCost() == null || !request.currency().equals(candidate.currency())) {
                return false;
            }
            BigDecimal scaledCost = candidate.estimatedCost()
                    .multiply(BigDecimal.valueOf(request.servings()))
                    .divide(BigDecimal.valueOf(candidate.defaultServings()), 2, java.math.RoundingMode.HALF_UP);
            if (scaledCost.compareTo(request.maxBudget()) > 0) {
                return false;
            }
        }
        if (!candidate.dietaryTagCodes().containsAll(rules.requiredDietaryTagCodes())) {
            return false;
        }
        if (candidate.allergenCodes().stream().anyMatch(rules.avoidAllergenCodes()::contains)) {
            return false;
        }
        return ingredientOverlap(candidate, request.ingredients());
    }

    private boolean ingredientOverlap(RecipeCandidate candidate, List<CookingPlanInput> inputs) {
        List<String> candidateNames = candidate.ingredients().stream()
                .map(ingredient -> normalise(ingredient.ingredientName()))
                .toList();
        return inputs.stream()
                .map(input -> normalise(input.ingredientName()))
                .anyMatch(inputName -> candidateNames.stream()
                        .anyMatch(candidateName -> candidateName.contains(inputName) || inputName.contains(candidateName)));
    }

    private String normalise(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private List<String> dietaryTagCodes(UUID userId) {
        return jdbcTemplate.queryForList("""
                SELECT dt.code
                FROM user_dietary_tag udt
                JOIN dietary_tag dt ON dt.id = udt.dietary_tag_id
                WHERE udt.user_id = :userId
                ORDER BY dt.code
                """,
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private List<String> allergenCodes(UUID userId) {
        return jdbcTemplate.queryForList("""
                SELECT a.code
                FROM user_allergen ua
                JOIN allergen a ON a.id = ua.allergen_id
                WHERE ua.user_id = :userId
                ORDER BY a.code
                """,
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private Map<UUID, List<RecipeIngredientSnapshot>> ingredients(List<RecipeHeader> headers) {
        Map<UUID, List<RecipeIngredientSnapshot>> result = emptyMap(headers);
        if (headers.isEmpty()) {
            return result;
        }
        jdbcTemplate.query("""
                SELECT ri.recipe_id,
                       ri.sequence_no,
                       i.canonical_name,
                       ri.quantity,
                       ri.unit,
                       ri.optional
                FROM recipe_ingredient ri
                JOIN ingredient i ON i.id = ri.ingredient_id
                WHERE ri.recipe_id IN (:recipeIds)
                ORDER BY ri.recipe_id, ri.sequence_no
                """,
                new MapSqlParameterSource("recipeIds", headers.stream().map(RecipeHeader::id).toList()),
                (rs, rowNum) -> {
                    UUID recipeId = rs.getObject("recipe_id", UUID.class);
                    result.computeIfAbsent(recipeId, ignored -> new ArrayList<>())
                            .add(new RecipeIngredientSnapshot(
                                    rs.getInt("sequence_no"),
                                    rs.getString("canonical_name"),
                                    rs.getBigDecimal("quantity"),
                                    rs.getString("unit"),
                                    rs.getBoolean("optional")));
                    return null;
                });
        return result;
    }

    private Map<UUID, List<RecipeStepSnapshot>> steps(List<RecipeHeader> headers) {
        Map<UUID, List<RecipeStepSnapshot>> result = emptyMap(headers);
        if (headers.isEmpty()) {
            return result;
        }
        jdbcTemplate.query("""
                SELECT recipe_id, step_no, instruction
                FROM recipe_step
                WHERE recipe_id IN (:recipeIds)
                ORDER BY recipe_id, step_no
                """,
                new MapSqlParameterSource("recipeIds", headers.stream().map(RecipeHeader::id).toList()),
                (rs, rowNum) -> {
                    UUID recipeId = rs.getObject("recipe_id", UUID.class);
                    result.computeIfAbsent(recipeId, ignored -> new ArrayList<>())
                            .add(new RecipeStepSnapshot(rs.getInt("step_no"), rs.getString("instruction")));
                    return null;
                });
        return result;
    }

    private <T> Map<UUID, List<T>> emptyMap(List<RecipeHeader> headers) {
        Map<UUID, List<T>> result = new LinkedHashMap<>();
        headers.forEach(header -> result.put(header.id(), new ArrayList<>()));
        return result;
    }

    private RecipeHeader headerRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecipeHeader(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("default_servings"),
                rs.getInt("total_minutes"),
                rs.getBigDecimal("estimated_cost"),
                trim(rs.getString("currency")),
                stringArray(rs.getArray("dietary_codes")),
                stringArray(rs.getArray("allergen_codes")));
    }

    private List<String> stringArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String.class::cast).toList();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record RecipeHeader(
            UUID id,
            String name,
            String description,
            int defaultServings,
            int totalMinutes,
            BigDecimal estimatedCost,
            String currency,
            List<String> dietaryCodes,
            List<String> allergenCodes) {

        RecipeCandidate toCandidate(List<RecipeIngredientSnapshot> ingredients, List<RecipeStepSnapshot> steps) {
            return new RecipeCandidate(
                    id,
                    name,
                    description,
                    defaultServings,
                    totalMinutes,
                    estimatedCost,
                    currency,
                    dietaryCodes,
                    allergenCodes,
                    ingredients,
                    steps);
        }
    }
}
