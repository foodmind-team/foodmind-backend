package com.foodmind.foodmindbackend.recipe.importing.infrastructure.persistence;

import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportQuestion;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
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
public class JdbcRecipeImportRepository implements RecipeImportRepository {
    private static final TypeReference<List<RecipeImportDraft>> DRAFTS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<RecipeImportQuestion>> QUESTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<RecipeImportAnswer>> ANSWERS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<UUID>> UUIDS_TYPE = new TypeReference<>() {};

    private static final String SELECT_COLUMNS = """
            SELECT id, owner_user_id, source_text, status, drafts_json, questions_json, answers_json,
                   created_recipe_ids_json, failure_code, failure_message, created_at, updated_at,
                   completed_at, version
            FROM public.recipe_import_session
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcRecipeImportRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecipeImportSession create(RecipeImportSession session) {
        jdbc.update("""
                INSERT INTO public.recipe_import_session (
                    id, owner_user_id, source_text, status, drafts_json, questions_json, answers_json,
                    created_recipe_ids_json, failure_code, failure_message, created_at, updated_at,
                    completed_at, version)
                VALUES (
                    :id, :owner, :sourceText, :status, CAST(:drafts AS jsonb), CAST(:questions AS jsonb),
                    CAST(:answers AS jsonb), CAST(:createdRecipeIds AS jsonb), :failureCode, :failureMessage,
                    LEAST(:createdAt, CURRENT_TIMESTAMP), LEAST(:updatedAt, CURRENT_TIMESTAMP),
                    :completedAt, :version)
                """, parameters(session));
        return findOwned(session.ownerUserId(), session.id()).orElseThrow();
    }

    @Override
    public Optional<RecipeImportSession> findOwned(UUID ownerUserId, UUID importId) {
        return jdbc.query(
                SELECT_COLUMNS + " WHERE id = :id AND owner_user_id = :owner",
                ownerParameters(ownerUserId, importId),
                mapper()).stream().findFirst();
    }

    @Override
    public Optional<RecipeImportSession> lockOwned(UUID ownerUserId, UUID importId) {
        return jdbc.query(
                SELECT_COLUMNS + " WHERE id = :id AND owner_user_id = :owner FOR UPDATE",
                ownerParameters(ownerUserId, importId),
                mapper()).stream().findFirst();
    }

    @Override
    public Optional<RecipeImportSession> updateAgentResult(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            RecipeImportStatus status,
            List<RecipeImportDraft> drafts,
            List<RecipeImportQuestion> questions,
            List<RecipeImportAnswer> answers) {
        int changed = jdbc.update("""
                UPDATE public.recipe_import_session
                SET status = :status,
                    drafts_json = CAST(:drafts AS jsonb),
                    questions_json = CAST(:questions AS jsonb),
                    answers_json = CAST(:answers AS jsonb),
                    version = version + 1
                WHERE id = :id AND owner_user_id = :owner AND version = :expectedVersion
                  AND status IN ('PROCESSING', 'NEEDS_CLARIFICATION')
                """, ownerParameters(ownerUserId, importId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("status", status.name())
                .addValue("drafts", json(drafts))
                .addValue("questions", json(questions))
                .addValue("answers", json(answers)));
        return changed == 0 ? Optional.empty() : findOwned(ownerUserId, importId);
    }

    @Override
    public Optional<RecipeImportSession> markFailed(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            String failureCode,
            String failureMessage) {
        int changed = jdbc.update("""
                UPDATE public.recipe_import_session
                SET status = 'FAILED', failure_code = :failureCode, failure_message = :failureMessage,
                    version = version + 1
                WHERE id = :id AND owner_user_id = :owner AND version = :expectedVersion
                  AND status IN ('PROCESSING', 'NEEDS_CLARIFICATION')
                """, ownerParameters(ownerUserId, importId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("failureCode", failureCode)
                .addValue("failureMessage", failureMessage));
        return changed == 0 ? Optional.empty() : findOwned(ownerUserId, importId);
    }

    @Override
    public Optional<RecipeImportSession> markCompleted(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            List<UUID> createdRecipeIds,
            OffsetDateTime completedAt) {
        int changed = jdbc.update("""
                UPDATE public.recipe_import_session
                SET status = 'COMPLETED', created_recipe_ids_json = CAST(:recipeIds AS jsonb),
                    completed_at = :completedAt, version = version + 1
                WHERE id = :id AND owner_user_id = :owner AND version = :expectedVersion
                  AND status = 'READY'
                """, ownerParameters(ownerUserId, importId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("recipeIds", json(createdRecipeIds))
                .addValue("completedAt", completedAt));
        return changed == 0 ? Optional.empty() : findOwned(ownerUserId, importId);
    }

    private MapSqlParameterSource parameters(RecipeImportSession session) {
        return ownerParameters(session.ownerUserId(), session.id())
                .addValue("sourceText", session.sourceText())
                .addValue("status", session.status().name())
                .addValue("drafts", json(session.drafts()))
                .addValue("questions", json(session.questions()))
                .addValue("answers", json(session.answers()))
                .addValue("createdRecipeIds", json(session.createdRecipeIds()))
                .addValue("failureCode", session.failureCode())
                .addValue("failureMessage", session.failureMessage())
                .addValue("createdAt", session.createdAt())
                .addValue("updatedAt", session.updatedAt())
                .addValue("completedAt", session.completedAt())
                .addValue("version", session.version());
    }

    private MapSqlParameterSource ownerParameters(UUID ownerUserId, UUID importId) {
        return new MapSqlParameterSource().addValue("owner", ownerUserId).addValue("id", importId);
    }

    private RowMapper<RecipeImportSession> mapper() {
        return (resultSet, rowNumber) -> new RecipeImportSession(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("owner_user_id", UUID.class),
                resultSet.getString("source_text"),
                RecipeImportStatus.valueOf(resultSet.getString("status")),
                parse(resultSet.getString("drafts_json"), DRAFTS_TYPE),
                parse(resultSet.getString("questions_json"), QUESTIONS_TYPE),
                parse(resultSet.getString("answers_json"), ANSWERS_TYPE),
                parse(resultSet.getString("created_recipe_ids_json"), UUIDS_TYPE),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getLong("version"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not encode recipe-import JSON", exception);
        }
    }

    private <T> T parse(String value, TypeReference<T> type) throws SQLException {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new SQLException("Invalid recipe-import JSON", exception);
        }
    }
}
