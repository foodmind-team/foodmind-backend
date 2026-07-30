package com.foodmind.foodmindbackend.recommendation.infrastructure.persistence;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationFeedbackRepository;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEvent;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEventType;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackTarget;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRejectionReason;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Repository
public class JdbcRecommendationFeedbackRepository implements RecommendationFeedbackRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRecommendationFeedbackRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RecommendationFeedbackTarget> findTarget(UUID userId, UUID sessionId, UUID candidateId) {
        return jdbcTemplate.query("""
                SELECT session.id AS session_id,
                       session.user_id,
                       candidate.id AS candidate_id,
                       candidate.place_meal_id,
                       offering.meal_id,
                       offering.place_id,
                       candidate.eligibility_status
                FROM recommendation_session AS session
                JOIN recommendation_candidate AS candidate
                  ON candidate.session_id = session.id
                JOIN place_meal AS offering
                  ON offering.id = candidate.place_meal_id
                WHERE session.id = :sessionId
                  AND session.user_id = :userId
                  AND candidate.id = :candidateId
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId)
                        .addValue("candidateId", candidateId),
                this::targetRow)
                .stream()
                .findFirst();
    }

    @Override
    public boolean sessionOwnedBy(UUID userId, UUID sessionId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM recommendation_session
                    WHERE id = :sessionId
                      AND user_id = :userId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<RecommendationFeedbackEventType> existingTerminalDecision(UUID userId, UUID sessionId, UUID candidateId) {
        return jdbcTemplate.query("""
                SELECT event_type
                FROM recommendation_feedback
                WHERE user_id = :userId
                  AND session_id = :sessionId
                  AND candidate_id = :candidateId
                  AND event_type IN ('ACCEPTED', 'REJECTED')
                ORDER BY created_at ASC, id ASC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("sessionId", sessionId)
                        .addValue("candidateId", candidateId),
                (rs, rowNum) -> RecommendationFeedbackEventType.valueOf(rs.getString("event_type")))
                .stream()
                .findFirst();
    }

    @Override
    public boolean resultingFoodRecordMatches(UUID userId, UUID foodRecordId, UUID mealId, UUID placeId) {
        Boolean matches = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM food_record
                    WHERE id = :foodRecordId
                      AND owner_user_id = :userId
                      AND deleted_at IS NULL
                      AND (meal_id IS NULL OR meal_id = :mealId)
                      AND (place_id IS NULL OR place_id = :placeId)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("foodRecordId", foodRecordId)
                        .addValue("userId", userId)
                        .addValue("mealId", mealId)
                        .addValue("placeId", placeId),
                Boolean.class);
        return Boolean.TRUE.equals(matches);
    }

    @Override
    public RecommendationFeedbackEvent insertOrResolveRetry(
            RecommendationFeedbackEvent event,
            String canonicalPayloadHash) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO recommendation_feedback (
                        id, session_id, candidate_id, user_id, event_type, reason_code, rating,
                        boolean_value, resulting_food_record_id, effective_until, idempotency_key
                    )
                    VALUES (
                        :id, :sessionId, :candidateId, :userId, :eventType, :reasonCode, :rating,
                        :booleanValue, :resultingFoodRecordId, :effectiveUntil, :idempotencyKey
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", event.id())
                            .addValue("sessionId", event.sessionId())
                            .addValue("candidateId", event.candidateId())
                            .addValue("userId", event.userId())
                            .addValue("eventType", event.eventType().name())
                            .addValue("reasonCode", event.reasonCode() == null ? null : event.reasonCode().name())
                            .addValue("rating", event.rating())
                            .addValue("booleanValue", event.booleanValue())
                            .addValue("resultingFoodRecordId", event.resultingFoodRecordId())
                            .addValue("effectiveUntil", event.effectiveUntil())
                            .addValue("idempotencyKey", event.idempotencyKey()));
        } catch (DuplicateKeyException exception) {
            return findByUserAndKey(event.userId(), event.idempotencyKey())
                    .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT));
        }
        return findById(event.userId(), event.id())
                .orElseThrow(() -> new IllegalStateException("Inserted feedback event is not readable."));
    }

    @Override
    public Optional<RecommendationFeedbackEvent> findById(UUID userId, UUID eventId) {
        return jdbcTemplate.query("""
                SELECT id, session_id, candidate_id, user_id, event_type, reason_code, rating,
                       boolean_value, resulting_food_record_id, effective_until, idempotency_key, created_at
                FROM recommendation_feedback
                WHERE id = :eventId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("userId", userId),
                this::eventRow)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<OffsetDateTime> latestTemporaryConstraint(UUID userId, UUID candidateId) {
        return jdbcTemplate.query("""
                SELECT effective_until
                FROM recommendation_feedback
                WHERE user_id = :userId
                  AND candidate_id = :candidateId
                  AND event_type = 'REJECTED'
                  AND effective_until > CURRENT_TIMESTAMP
                ORDER BY effective_until DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("candidateId", candidateId),
                (rs, rowNum) -> rs.getObject("effective_until", OffsetDateTime.class))
                .stream()
                .findFirst();
    }

    private Optional<RecommendationFeedbackEvent> findByUserAndKey(UUID userId, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT id, session_id, candidate_id, user_id, event_type, reason_code, rating,
                       boolean_value, resulting_food_record_id, effective_until, idempotency_key, created_at
                FROM recommendation_feedback
                WHERE user_id = :userId
                  AND idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("idempotencyKey", idempotencyKey),
                this::eventRow)
                .stream()
                .findFirst();
    }

    private RecommendationFeedbackTarget targetRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecommendationFeedbackTarget(
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("candidate_id", UUID.class),
                rs.getObject("place_meal_id", UUID.class),
                rs.getObject("meal_id", UUID.class),
                rs.getObject("place_id", UUID.class),
                rs.getString("eligibility_status"));
    }

    private RecommendationFeedbackEvent eventRow(ResultSet rs, int rowNum) throws SQLException {
        String reason = rs.getString("reason_code");
        return new RecommendationFeedbackEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("candidate_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                RecommendationFeedbackEventType.valueOf(rs.getString("event_type")),
                reason == null ? null : RecommendationRejectionReason.valueOf(reason),
                rs.getBigDecimal("rating"),
                (Boolean) rs.getObject("boolean_value"),
                rs.getObject("resulting_food_record_id", UUID.class),
                rs.getObject("effective_until", OffsetDateTime.class),
                rs.getString("idempotency_key"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
