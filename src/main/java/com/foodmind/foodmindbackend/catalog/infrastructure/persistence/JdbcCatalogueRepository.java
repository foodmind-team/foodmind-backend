package com.foodmind.foodmindbackend.catalog.infrastructure.persistence;

import com.foodmind.foodmindbackend.catalog.application.port.CatalogueCandidateQuery;
import com.foodmind.foodmindbackend.catalog.application.port.CatalogueDetailQuery;
import com.foodmind.foodmindbackend.catalog.application.port.CatalogueReferenceDataQuery;
import com.foodmind.foodmindbackend.catalog.domain.CatalogueReferenceData;
import com.foodmind.foodmindbackend.catalog.domain.GeoPoint;
import com.foodmind.foodmindbackend.catalog.domain.MealDetail;
import com.foodmind.foodmindbackend.catalog.domain.MealOffering;
import com.foodmind.foodmindbackend.catalog.domain.Money;
import com.foodmind.foodmindbackend.catalog.domain.OfferingCandidate;
import com.foodmind.foodmindbackend.catalog.domain.PlaceDetail;
import com.foodmind.foodmindbackend.catalog.domain.PlaceObservation;
import com.foodmind.foodmindbackend.catalog.domain.PlaceOffering;
import com.foodmind.foodmindbackend.catalog.domain.PlaceSummary;
import com.foodmind.foodmindbackend.catalog.domain.ProductDetail;
import com.foodmind.foodmindbackend.catalog.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.catalog.domain.ReferenceItem;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * @date: 29/7/2026 9:36 pm
 */

@Repository
public class JdbcCatalogueRepository implements CatalogueReferenceDataQuery, CatalogueDetailQuery, CatalogueCandidateQuery {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcCatalogueRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    @Override
    public CatalogueReferenceData referenceData() {
        return new CatalogueReferenceData(
                referenceItems("cuisine"),
                referenceItems("dietary_tag"),
                referenceItems("allergen"),
                distinctActiveValues("meal", "meal_type"),
                distinctActiveValues("place", "place_type"));
    }

    @Override
    public Optional<ReferenceItem> findCuisineByCode(String code) {
        return findReferenceByCode("cuisine", code);
    }

    @Override
    public Optional<ReferenceItem> findDietaryTagByCode(String code) {
        return findReferenceByCode("dietary_tag", code);
    }

    @Override
    public Optional<ReferenceItem> findAllergenByCode(String code) {
        return findReferenceByCode("allergen", code);
    }

    @Override
    public Optional<MealDetail> findActiveMeal(UUID id) {
        return jdbcTemplate.query("""
                        SELECT m.id, m.name, m.description, m.meal_type, m.default_spice_level,
                               c.id AS cuisine_id, c.code AS cuisine_code, c.name AS cuisine_name
                        FROM meal m
                        JOIN cuisine c ON c.id = m.cuisine_id
                        WHERE m.id = ? AND m.curation_status = 'ACTIVE'
                        """,
                (rs, rowNum) -> new MealDetail(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        new ReferenceItem(
                                rs.getObject("cuisine_id", UUID.class),
                                rs.getString("cuisine_code"),
                                rs.getString("cuisine_name")),
                        rs.getString("meal_type"),
                        getInteger(rs, "default_spice_level"),
                        classificationCodes("meal_dietary_tag", "meal_id", "dietary_tag", "dietary_tag_id", id),
                        classificationCodes("meal_allergen", "meal_id", "allergen", "allergen_id", id),
                        mealOfferings(id)),
                id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PlaceDetail> findActivePlace(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, name, place_type, area, address_text, latitude, longitude, price_band
                        FROM place
                        WHERE id = ? AND curation_status = 'ACTIVE'
                        """,
                (rs, rowNum) -> new PlaceDetail(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("place_type"),
                        rs.getString("area"),
                        rs.getString("address_text"),
                        coordinates(rs),
                        getInteger(rs, "price_band"),
                        placeObservations(id),
                        placeOfferings(id)),
                id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProductDetail> findActiveProduct(UUID id) {
        return jdbcTemplate.query("""
                        SELECT fp.id, fp.name, fp.brand, fp.description, fp.price, fp.currency,
                               p.id AS place_id, p.name AS place_name, p.area AS place_area, p.place_type
                        FROM food_product fp
                        LEFT JOIN place p ON p.id = fp.place_id
                        WHERE fp.id = ?
                          AND fp.curation_status = 'ACTIVE'
                          AND (fp.place_id IS NULL OR p.curation_status = 'ACTIVE')
                        """,
                (rs, rowNum) -> new ProductDetail(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getString("description"),
                        nullableMoney(rs, "price", "currency"),
                        nullablePlaceSummary(rs),
                        classificationCodes(
                                "food_product_dietary_tag",
                                "food_product_id",
                                "dietary_tag",
                                "dietary_tag_id",
                                id),
                        classificationCodes(
                                "food_product_allergen",
                                "food_product_id",
                                "allergen",
                                "allergen_id",
                                id)),
                id)
                .stream()
                .findFirst();
    }

    @Override
    public List<OfferingCandidate> activeOfferingCandidates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<OfferingCandidateRow> rows = jdbcTemplate.query("""
                        SELECT pm.id AS offering_id, m.id AS meal_id, m.name AS meal_name, m.meal_type,
                               c.code AS cuisine_code, p.id AS place_id, p.name AS place_name, p.area,
                               pm.price, pm.currency, COALESCE(pm.spice_level, m.default_spice_level) AS spice_level,
                               po.score AS cleanliness_score, po.observed_at AS cleanliness_observed_at,
                               po.source_kind AS cleanliness_source_kind
                        FROM place_meal pm
                        JOIN meal m ON m.id = pm.meal_id
                        JOIN cuisine c ON c.id = m.cuisine_id
                        JOIN place p ON p.id = pm.place_id
                        LEFT JOIN LATERAL (
                            SELECT score, observed_at, source_kind
                            FROM place_observation
                            WHERE place_id = p.id AND observation_type = 'CLEANLINESS'
                            ORDER BY observed_at DESC, id
                            LIMIT 1
                        ) po ON true
                        WHERE pm.available
                          AND m.curation_status = 'ACTIVE'
                          AND p.curation_status = 'ACTIVE'
                        ORDER BY m.meal_type, pm.price, pm.id
                        LIMIT ?
                        """,
                this::offeringCandidateRow,
                safeLimit);

        List<UUID> mealIds = rows.stream().map(OfferingCandidateRow::mealId).distinct().toList();
        Map<UUID, List<String>> dietaryCodes = classificationCodesByOwner(
                "meal_dietary_tag",
                "meal_id",
                "dietary_tag",
                "dietary_tag_id",
                mealIds);
        Map<UUID, List<String>> allergenCodes = classificationCodesByOwner(
                "meal_allergen",
                "meal_id",
                "allergen",
                "allergen_id",
                mealIds);

        return rows.stream()
                .map(row -> new OfferingCandidate(
                        row.offeringId(),
                        row.mealId(),
                        row.mealName(),
                        row.mealType(),
                        row.cuisineCode(),
                        row.placeId(),
                        row.placeName(),
                        row.area(),
                        row.price(),
                        row.spiceLevel(),
                        row.cleanliness(),
                        dietaryCodes.getOrDefault(row.mealId(), List.of()),
                        allergenCodes.getOrDefault(row.mealId(), List.of())))
                .toList();
    }

    @Override
    public List<RecipeCandidate> controlledRecipeCandidates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<RecipeCandidateRow> rows = jdbcTemplate.query("""
                        SELECT r.id, r.name, r.description, c.code AS cuisine_code,
                               r.default_servings, r.prep_minutes, r.cook_minutes,
                               r.estimated_cost, r.currency
                        FROM recipe r
                        LEFT JOIN cuisine c ON c.id = r.cuisine_id
                        WHERE r.curation_status = 'ACTIVE'
                        ORDER BY r.name, r.id
                        LIMIT ?
                        """,
                this::recipeCandidateRow,
                safeLimit);
        List<UUID> recipeIds = rows.stream().map(RecipeCandidateRow::id).toList();
        Map<UUID, List<String>> dietaryCodes = classificationCodesByOwner(
                "recipe_dietary_tag",
                "recipe_id",
                "dietary_tag",
                "dietary_tag_id",
                recipeIds);
        Map<UUID, List<String>> allergenCodes = classificationCodesByOwner(
                "recipe_allergen",
                "recipe_id",
                "allergen",
                "allergen_id",
                recipeIds);
        Map<UUID, List<RecipeCandidate.IngredientLine>> ingredients = recipeIngredients(recipeIds);
        Map<UUID, List<RecipeCandidate.StepLine>> steps = recipeSteps(recipeIds);

        return rows.stream()
                .map(row -> new RecipeCandidate(
                        row.id(),
                        row.name(),
                        row.description(),
                        row.cuisineCode(),
                        row.defaultServings(),
                        row.prepMinutes(),
                        row.cookMinutes(),
                        row.estimatedCost(),
                        dietaryCodes.getOrDefault(row.id(), List.of()),
                        allergenCodes.getOrDefault(row.id(), List.of()),
                        ingredients.getOrDefault(row.id(), List.of()),
                        steps.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private List<ReferenceItem> referenceItems(String tableName) {
        return jdbcTemplate.query(
                "SELECT id, code, name FROM " + tableName + " ORDER BY code",
                this::referenceItem);
    }

    private Optional<ReferenceItem> findReferenceByCode(String tableName, String code) {
        return jdbcTemplate.query(
                        "SELECT id, code, name FROM " + tableName + " WHERE code = ?",
                        this::referenceItem,
                        code == null ? null : code.trim().toUpperCase())
                .stream()
                .findFirst();
    }

    private List<String> distinctActiveValues(String tableName, String columnName) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT " + columnName + " FROM " + tableName + " WHERE curation_status = 'ACTIVE' ORDER BY " + columnName,
                String.class);
    }

    private List<MealOffering> mealOfferings(UUID mealId) {
        return jdbcTemplate.query("""
                        SELECT pm.id, pm.display_name, pm.price, pm.currency, pm.spice_level, pm.availability_note,
                               p.id AS place_id, p.name AS place_name, p.area AS place_area, p.place_type
                        FROM place_meal pm
                        JOIN place p ON p.id = pm.place_id
                        WHERE pm.meal_id = ?
                          AND pm.available
                          AND p.curation_status = 'ACTIVE'
                        ORDER BY pm.price, p.name, pm.id
                        """,
                (rs, rowNum) -> new MealOffering(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        money(rs, "price", "currency"),
                        getInteger(rs, "spice_level"),
                        rs.getString("availability_note"),
                        new PlaceSummary(
                                rs.getObject("place_id", UUID.class),
                                rs.getString("place_name"),
                                rs.getString("place_area"),
                                rs.getString("place_type"))),
                mealId);
    }

    private List<PlaceObservation> placeObservations(UUID placeId) {
        return jdbcTemplate.query("""
                        SELECT id, observation_type, score, note, source_kind, observed_at
                        FROM place_observation
                        WHERE place_id = ?
                        ORDER BY observation_type, observed_at DESC, id
                        """,
                (rs, rowNum) -> new PlaceObservation(
                        rs.getObject("id", UUID.class),
                        rs.getString("observation_type"),
                        rs.getBigDecimal("score"),
                        rs.getString("note"),
                        rs.getString("source_kind"),
                        rs.getObject("observed_at", OffsetDateTime.class)),
                placeId);
    }

    private List<PlaceOffering> placeOfferings(UUID placeId) {
        return jdbcTemplate.query("""
                        SELECT pm.id, pm.display_name, pm.price, pm.currency, pm.spice_level, pm.availability_note,
                               m.id AS meal_id, m.name AS meal_name, m.meal_type, c.code AS cuisine_code
                        FROM place_meal pm
                        JOIN meal m ON m.id = pm.meal_id
                        JOIN cuisine c ON c.id = m.cuisine_id
                        WHERE pm.place_id = ?
                          AND pm.available
                          AND m.curation_status = 'ACTIVE'
                        ORDER BY pm.price, m.name, pm.id
                        """,
                (rs, rowNum) -> new PlaceOffering(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        money(rs, "price", "currency"),
                        getInteger(rs, "spice_level"),
                        rs.getString("availability_note"),
                        rs.getObject("meal_id", UUID.class),
                        rs.getString("meal_name"),
                        rs.getString("meal_type"),
                        rs.getString("cuisine_code")),
                placeId);
    }

    private List<String> classificationCodes(String joinTable, String ownerColumn, String referenceTable, String referenceColumn, UUID ownerId) {
        return jdbcTemplate.queryForList("""
                        SELECT r.code
                        FROM %s j
                        JOIN %s r ON r.id = j.%s
                        WHERE j.%s = ?
                        ORDER BY r.code
                        """.formatted(joinTable, referenceTable, referenceColumn, ownerColumn),
                String.class,
                ownerId);
    }

    private Map<UUID, List<String>> classificationCodesByOwner(
            String joinTable,
            String ownerColumn,
            String referenceTable,
            String referenceColumn,
            List<UUID> ownerIds) {
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        ownerIds.forEach(ownerId -> result.put(ownerId, new ArrayList<>()));
        if (ownerIds.isEmpty()) {
            return result;
        }
        namedJdbcTemplate.query("""
                        SELECT j.%s AS owner_id, r.code
                        FROM %s j
                        JOIN %s r ON r.id = j.%s
                        WHERE j.%s IN (:ownerIds)
                        ORDER BY j.%s, r.code
                        """.formatted(ownerColumn, joinTable, referenceTable, referenceColumn, ownerColumn, ownerColumn),
                new MapSqlParameterSource("ownerIds", ownerIds),
                (RowMapper<Void>) (rs, rowNum) -> {
                    UUID ownerId = rs.getObject("owner_id", UUID.class);
                    result.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(rs.getString("code"));
                    return null;
                });
        return result;
    }

    private Map<UUID, List<RecipeCandidate.IngredientLine>> recipeIngredients(List<UUID> recipeIds) {
        Map<UUID, List<RecipeCandidate.IngredientLine>> result = new LinkedHashMap<>();
        recipeIds.forEach(recipeId -> result.put(recipeId, new ArrayList<>()));
        if (recipeIds.isEmpty()) {
            return result;
        }
        namedJdbcTemplate.query("""
                        SELECT ri.recipe_id, ri.sequence_no, i.id AS ingredient_id, i.canonical_name,
                               ri.quantity, ri.unit, ri.optional
                        FROM recipe_ingredient ri
                        JOIN ingredient i ON i.id = ri.ingredient_id
                        WHERE ri.recipe_id IN (:recipeIds)
                        ORDER BY ri.recipe_id, ri.sequence_no
                        """,
                new MapSqlParameterSource("recipeIds", recipeIds),
                (RowMapper<Void>) (rs, rowNum) -> {
                    UUID recipeId = rs.getObject("recipe_id", UUID.class);
                    result.computeIfAbsent(recipeId, ignored -> new ArrayList<>()).add(new RecipeCandidate.IngredientLine(
                            rs.getInt("sequence_no"),
                            rs.getObject("ingredient_id", UUID.class),
                            rs.getString("canonical_name"),
                            rs.getBigDecimal("quantity"),
                            rs.getString("unit"),
                            rs.getBoolean("optional")));
                    return null;
                });
        return result;
    }

    private Map<UUID, List<RecipeCandidate.StepLine>> recipeSteps(List<UUID> recipeIds) {
        Map<UUID, List<RecipeCandidate.StepLine>> result = new LinkedHashMap<>();
        recipeIds.forEach(recipeId -> result.put(recipeId, new ArrayList<>()));
        if (recipeIds.isEmpty()) {
            return result;
        }
        namedJdbcTemplate.query("""
                        SELECT recipe_id, step_no, instruction
                        FROM recipe_step
                        WHERE recipe_id IN (:recipeIds)
                        ORDER BY recipe_id, step_no
                        """,
                new MapSqlParameterSource("recipeIds", recipeIds),
                (RowMapper<Void>) (rs, rowNum) -> {
                    UUID recipeId = rs.getObject("recipe_id", UUID.class);
                    result.computeIfAbsent(recipeId, ignored -> new ArrayList<>()).add(new RecipeCandidate.StepLine(
                            rs.getInt("step_no"),
                            rs.getString("instruction")));
                    return null;
                });
        return result;
    }

    private ReferenceItem referenceItem(ResultSet rs, int rowNum) throws SQLException {
        return new ReferenceItem(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"));
    }

    private OfferingCandidateRow offeringCandidateRow(ResultSet rs, int rowNum) throws SQLException {
        OfferingCandidate.CleanlinessEvidence cleanliness = null;
        BigDecimal score = rs.getBigDecimal("cleanliness_score");
        if (!rs.wasNull()) {
            cleanliness = new OfferingCandidate.CleanlinessEvidence(
                    score,
                    rs.getObject("cleanliness_observed_at", OffsetDateTime.class),
                    rs.getString("cleanliness_source_kind"));
        }
        return new OfferingCandidateRow(
                rs.getObject("offering_id", UUID.class),
                rs.getObject("meal_id", UUID.class),
                rs.getString("meal_name"),
                rs.getString("meal_type"),
                rs.getString("cuisine_code"),
                rs.getObject("place_id", UUID.class),
                rs.getString("place_name"),
                rs.getString("area"),
                money(rs, "price", "currency"),
                getInteger(rs, "spice_level"),
                cleanliness);
    }

    private RecipeCandidateRow recipeCandidateRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecipeCandidateRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("cuisine_code"),
                rs.getInt("default_servings"),
                rs.getInt("prep_minutes"),
                rs.getInt("cook_minutes"),
                nullableMoney(rs, "estimated_cost", "currency"));
    }

    private GeoPoint coordinates(ResultSet rs) throws SQLException {
        BigDecimal latitude = rs.getBigDecimal("latitude");
        if (rs.wasNull()) {
            return null;
        }
        return new GeoPoint(latitude, rs.getBigDecimal("longitude"));
    }

    private PlaceSummary nullablePlaceSummary(ResultSet rs) throws SQLException {
        UUID placeId = rs.getObject("place_id", UUID.class);
        if (rs.wasNull() || placeId == null) {
            return null;
        }
        return new PlaceSummary(
                placeId,
                rs.getString("place_name"),
                rs.getString("place_area"),
                rs.getString("place_type"));
    }

    private Money nullableMoney(ResultSet rs, String amountColumn, String currencyColumn) throws SQLException {
        BigDecimal amount = rs.getBigDecimal(amountColumn);
        if (rs.wasNull()) {
            return null;
        }
        return new Money(amount, rs.getString(currencyColumn).trim());
    }

    private Money money(ResultSet rs, String amountColumn, String currencyColumn) throws SQLException {
        return new Money(rs.getBigDecimal(amountColumn), rs.getString(currencyColumn).trim());
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record OfferingCandidateRow(
            UUID offeringId,
            UUID mealId,
            String mealName,
            String mealType,
            String cuisineCode,
            UUID placeId,
            String placeName,
            String area,
            Money price,
            Integer spiceLevel,
            OfferingCandidate.CleanlinessEvidence cleanliness) {
    }

    private record RecipeCandidateRow(
            UUID id,
            String name,
            String description,
            String cuisineCode,
            int defaultServings,
            int prepMinutes,
            int cookMinutes,
            Money estimatedCost) {
    }
}
