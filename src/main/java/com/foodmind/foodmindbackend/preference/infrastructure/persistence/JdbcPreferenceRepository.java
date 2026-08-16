package com.foodmind.foodmindbackend.preference.infrastructure.persistence;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.preference.application.port.PreferenceCommandRepository;
import com.foodmind.foodmindbackend.preference.application.port.PreferenceQuery;
import com.foodmind.foodmindbackend.preference.domain.AllergenPreference;
import com.foodmind.foodmindbackend.preference.domain.PreferenceReplacement;
import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

@Repository
public class JdbcPreferenceRepository implements PreferenceQuery, PreferenceCommandRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcPreferenceRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    @Override
    public PreferenceSnapshot snapshotForUser(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT user_id, budget_min, budget_max, currency, spice_tolerance, preferred_area,
                               preferred_latitude, preferred_longitude, max_distance_km, cleanliness_priority,
                               minimum_cleanliness_evidence_score, food_goal, drink_sweetness_preference,
                               drink_ice_preference, cooking_region, created_at, updated_at, version
                        FROM user_preference
                        WHERE user_id = ?
                        """,
                (rs, rowNum) -> scalarSnapshot(rs, userId),
                userId)
                .stream()
                .findFirst()
                .orElseGet(() -> emptySnapshot(userId));
    }

    @Override
    public PreferenceSnapshot replace(UUID userId, PreferenceReplacement replacement) {
        Map<String, UUID> likedCuisines = resolveReferenceCodes("cuisine", replacement.likedCuisineCodes(), "likedCuisineCodes");
        Map<String, UUID> dislikedCuisines = resolveReferenceCodes("cuisine", replacement.dislikedCuisineCodes(), "dislikedCuisineCodes");
        Map<String, UUID> dietaryTags = resolveReferenceCodes("dietary_tag", replacement.dietaryTagCodes(), "dietaryTagCodes");
        Map<String, UUID> allergens = resolveReferenceCodes(
                "allergen",
                replacement.allergens().stream().map(AllergenPreference::code).toList(),
                "allergens.code");

        upsertScalarPreferences(userId, replacement);
        deleteExistingJoins(userId);
        insertCuisinePreferences(userId, likedCuisines, "LIKE");
        insertCuisinePreferences(userId, dislikedCuisines, "DISLIKE");
        insertDietaryTags(userId, dietaryTags);
        insertAllergens(userId, allergens, replacement.allergens());
        insertMealTypes(userId, replacement.preferredMealTypes());

        return snapshotForUser(userId);
    }

    @Override
    public PreferenceSnapshot updateCookingRegion(UUID userId, String cookingRegion) {
        jdbcTemplate.update("""
                        INSERT INTO user_preference (user_id, cooking_region)
                        VALUES (?, ?)
                        ON CONFLICT (user_id) DO UPDATE SET
                            cooking_region = EXCLUDED.cooking_region,
                            version = user_preference.version + 1
                        """,
                userId,
                cookingRegion);
        return snapshotForUser(userId);
    }

    private PreferenceSnapshot scalarSnapshot(ResultSet rs, UUID userId) throws SQLException {
        return new PreferenceSnapshot(
                userId,
                rs.getBigDecimal("budget_min"),
                rs.getBigDecimal("budget_max"),
                rs.getString("currency").trim(),
                getInteger(rs, "spice_tolerance"),
                rs.getString("preferred_area"),
                rs.getBigDecimal("preferred_latitude"),
                rs.getBigDecimal("preferred_longitude"),
                rs.getBigDecimal("max_distance_km"),
                getInteger(rs, "cleanliness_priority"),
                rs.getBigDecimal("minimum_cleanliness_evidence_score"),
                rs.getString("food_goal"),
                rs.getString("drink_sweetness_preference"),
                rs.getString("drink_ice_preference"),
                rs.getString("cooking_region"),
                cuisineCodes(userId, "LIKE"),
                cuisineCodes(userId, "DISLIKE"),
                dietaryTagCodes(userId),
                allergenPreferences(userId),
                preferredMealTypes(userId),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getLong("version"));
    }

    private PreferenceSnapshot emptySnapshot(UUID userId) {
        return new PreferenceSnapshot(
                userId,
                null,
                null,
                "SGD",
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                "SG",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                0);
    }

    private void upsertScalarPreferences(UUID userId, PreferenceReplacement replacement) {
        jdbcTemplate.update("""
                        INSERT INTO user_preference (
                            user_id, budget_min, budget_max, currency, spice_tolerance, preferred_area,
                            preferred_latitude, preferred_longitude, max_distance_km, cleanliness_priority,
                            minimum_cleanliness_evidence_score, food_goal, drink_sweetness_preference,
                            drink_ice_preference, cooking_region, version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'SG'), 0)
                        ON CONFLICT (user_id) DO UPDATE SET
                            budget_min = EXCLUDED.budget_min,
                            budget_max = EXCLUDED.budget_max,
                            currency = EXCLUDED.currency,
                            spice_tolerance = EXCLUDED.spice_tolerance,
                            preferred_area = EXCLUDED.preferred_area,
                            preferred_latitude = EXCLUDED.preferred_latitude,
                            preferred_longitude = EXCLUDED.preferred_longitude,
                            max_distance_km = EXCLUDED.max_distance_km,
                            cleanliness_priority = EXCLUDED.cleanliness_priority,
                            minimum_cleanliness_evidence_score = EXCLUDED.minimum_cleanliness_evidence_score,
                            food_goal = EXCLUDED.food_goal,
                            drink_sweetness_preference = EXCLUDED.drink_sweetness_preference,
                            drink_ice_preference = EXCLUDED.drink_ice_preference,
                            cooking_region = COALESCE(?, user_preference.cooking_region),
                            version = user_preference.version + 1
                        """,
                userId,
                replacement.budgetMin(),
                replacement.budgetMax(),
                replacement.currency(),
                replacement.spiceTolerance(),
                replacement.preferredArea(),
                replacement.preferredLatitude(),
                replacement.preferredLongitude(),
                replacement.maxDistanceKm(),
                replacement.cleanlinessPriority(),
                replacement.minimumCleanlinessEvidenceScore(),
                replacement.foodGoal(),
                replacement.drinkSweetnessPreference(),
                replacement.drinkIcePreference(),
                replacement.cookingRegion(),
                replacement.cookingRegion());
    }

    private void deleteExistingJoins(UUID userId) {
        jdbcTemplate.update("DELETE FROM user_cuisine_preference WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_dietary_tag WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_allergen WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_preferred_meal_type WHERE user_id = ?", userId);
    }

    private void insertCuisinePreferences(UUID userId, Map<String, UUID> cuisines, String preference) {
        cuisines.values().forEach(cuisineId -> jdbcTemplate.update("""
                        INSERT INTO user_cuisine_preference (user_id, cuisine_id, preference)
                        VALUES (?, ?, ?)
                        """,
                userId,
                cuisineId,
                preference));
    }

    private void insertDietaryTags(UUID userId, Map<String, UUID> dietaryTags) {
        dietaryTags.values().forEach(dietaryTagId -> jdbcTemplate.update("""
                        INSERT INTO user_dietary_tag (user_id, dietary_tag_id)
                        VALUES (?, ?)
                        """,
                userId,
                dietaryTagId));
    }

    private void insertAllergens(UUID userId, Map<String, UUID> allergens, List<AllergenPreference> preferences) {
        preferences.stream()
                .sorted((first, second) -> first.code().compareTo(second.code()))
                .forEach(preference -> jdbcTemplate.update("""
                                INSERT INTO user_allergen (user_id, allergen_id, severity)
                                VALUES (?, ?, ?)
                                """,
                        userId,
                        allergens.get(preference.code()),
                        preference.severity()));
    }

    private void insertMealTypes(UUID userId, List<String> mealTypes) {
        mealTypes.forEach(mealType -> jdbcTemplate.update("""
                        INSERT INTO user_preferred_meal_type (user_id, meal_type)
                        VALUES (?, ?)
                        """,
                userId,
                mealType));
    }

    private Map<String, UUID> resolveReferenceCodes(String table, List<String> requestedCodes, String field) {
        if (requestedCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> resolved = new LinkedHashMap<>();
        namedJdbcTemplate.query(
                "SELECT code, id FROM " + table + " WHERE code IN (:codes) ORDER BY code",
                new MapSqlParameterSource("codes", requestedCodes),
                (RowMapper<Void>) (rs, rowNum) -> {
                    resolved.put(rs.getString("code"), rs.getObject("id", UUID.class));
                    return null;
                });
        List<ApiFieldError> unknown = requestedCodes.stream()
                .filter(code -> !resolved.containsKey(code))
                .map(code -> new ApiFieldError(field, "UNKNOWN_REFERENCE_CODE", "Reference code is not supported: " + code))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    unknown);
        }
        return resolved;
    }

    private List<String> cuisineCodes(UUID userId, String preference) {
        return jdbcTemplate.queryForList("""
                        SELECT c.code
                        FROM user_cuisine_preference ucp
                        JOIN cuisine c ON c.id = ucp.cuisine_id
                        WHERE ucp.user_id = ? AND ucp.preference = ?
                        ORDER BY c.code
                        """,
                String.class,
                userId,
                preference);
    }

    private List<String> dietaryTagCodes(UUID userId) {
        return jdbcTemplate.queryForList("""
                        SELECT dt.code
                        FROM user_dietary_tag udt
                        JOIN dietary_tag dt ON dt.id = udt.dietary_tag_id
                        WHERE udt.user_id = ?
                        ORDER BY dt.code
                        """,
                String.class,
                userId);
    }

    private List<AllergenPreference> allergenPreferences(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT a.code, ua.severity
                        FROM user_allergen ua
                        JOIN allergen a ON a.id = ua.allergen_id
                        WHERE ua.user_id = ?
                        ORDER BY a.code
                        """,
                (rs, rowNum) -> new AllergenPreference(rs.getString("code"), rs.getString("severity")),
                userId);
    }

    private List<String> preferredMealTypes(UUID userId) {
        return jdbcTemplate.queryForList("""
                        SELECT meal_type
                        FROM user_preferred_meal_type
                        WHERE user_id = ?
                        ORDER BY meal_type
                        """,
                String.class,
                userId);
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
