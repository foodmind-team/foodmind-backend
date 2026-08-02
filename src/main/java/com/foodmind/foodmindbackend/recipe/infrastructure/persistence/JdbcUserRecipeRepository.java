package com.foodmind.foodmindbackend.recipe.infrastructure.persistence;

import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipePage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcUserRecipeRepository implements UserRecipeRepository {
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcUserRecipeRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserRecipe create(UserRecipe recipe) {
        jdbc.update("""
                INSERT INTO user_recipe (id, owner_user_id, name, servings, image_url, tags_json, allergen_hints_json, ingredients_json, steps_json, created_at, updated_at, version)
                VALUES (:id, :owner, :name, :servings, :imageUrl, CAST(:tags AS jsonb), CAST(:allergens AS jsonb), CAST(:ingredients AS jsonb), CAST(:steps AS jsonb), :createdAt, :updatedAt, :version)
                """, params(recipe));
        return findOwned(recipe.ownerUserId(), recipe.id()).orElseThrow();
    }

    @Override
    public Optional<UserRecipe> findOwned(UUID ownerUserId, UUID recipeId) {
        return jdbc.query("""
                SELECT id, owner_user_id, name, servings, image_url, tags_json, allergen_hints_json, ingredients_json, steps_json, created_at, updated_at, version
                FROM user_recipe WHERE id = :id AND owner_user_id = :owner AND deleted_at IS NULL
                """, new MapSqlParameterSource().addValue("id", recipeId).addValue("owner", ownerUserId), mapper()).stream().findFirst();
    }

    @Override
    public UserRecipePage findOwnedPage(UUID ownerUserId, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("owner", ownerUserId).addValue("limit", size).addValue("offset", page * size);
        List<UserRecipe> items = jdbc.query("""
                SELECT id, owner_user_id, name, servings, image_url, tags_json, allergen_hints_json, ingredients_json, steps_json, created_at, updated_at, version
                FROM user_recipe WHERE owner_user_id = :owner AND deleted_at IS NULL
                ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset
                """, params, mapper());
        Long total = jdbc.queryForObject("SELECT count(*) FROM user_recipe WHERE owner_user_id = :owner AND deleted_at IS NULL", new MapSqlParameterSource("owner", ownerUserId), Long.class);
        return new UserRecipePage(items, total == null ? 0 : total);
    }

    @Override
    public Optional<UserRecipe> update(UserRecipe recipe, long expectedVersion) {
        int changed = jdbc.update("""
                UPDATE user_recipe SET name = :name, servings = :servings, image_url = :imageUrl,
                    tags_json = CAST(:tags AS jsonb), allergen_hints_json = CAST(:allergens AS jsonb),
                    ingredients_json = CAST(:ingredients AS jsonb), steps_json = CAST(:steps AS jsonb),
                    updated_at = :updatedAt, version = :version
                WHERE id = :id AND owner_user_id = :owner AND deleted_at IS NULL AND version = :expectedVersion
                """, params(recipe).addValue("expectedVersion", expectedVersion));
        return changed == 0 ? Optional.empty() : findOwned(recipe.ownerUserId(), recipe.id());
    }

    @Override
    public boolean deleteOwned(UUID ownerUserId, UUID recipeId) {
        return jdbc.update("UPDATE user_recipe SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id AND owner_user_id = :owner AND deleted_at IS NULL",
                new MapSqlParameterSource().addValue("id", recipeId).addValue("owner", ownerUserId)) > 0;
    }

    private MapSqlParameterSource params(UserRecipe recipe) {
        return new MapSqlParameterSource()
                .addValue("id", recipe.id()).addValue("owner", recipe.ownerUserId()).addValue("name", recipe.name())
                .addValue("servings", recipe.servings()).addValue("imageUrl", recipe.imageUrl())
                .addValue("tags", json(recipe.tags())).addValue("allergens", json(recipe.allergenHints()))
                .addValue("ingredients", json(recipe.ingredients())).addValue("steps", json(recipe.steps()))
                .addValue("createdAt", recipe.createdAt()).addValue("updatedAt", recipe.updatedAt()).addValue("version", recipe.version());
    }

    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (JacksonException exception) { throw new IllegalStateException("Could not encode recipe JSON", exception); }
    }

    private RowMapper<UserRecipe> mapper() {
        return (rs, rowNum) -> new UserRecipe(rs.getObject("id", UUID.class), rs.getObject("owner_user_id", UUID.class), rs.getString("name"),
                rs.getInt("servings"), rs.getString("image_url"), parse(rs.getString("tags_json")), parse(rs.getString("allergen_hints_json")),
                parse(rs.getString("ingredients_json")), parse(rs.getString("steps_json")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
    }

    private List<String> parse(String value) throws SQLException {
        try { return objectMapper.readValue(value, LIST_TYPE); }
        catch (JacksonException exception) { throw new SQLException("Invalid recipe JSON", exception); }
    }
}
