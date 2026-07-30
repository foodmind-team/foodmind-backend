package com.foodmind.foodmindbackend.recommendation.infrastructure.persistence;

import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationSessionRepository;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationCandidateResult;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationSessionSummary;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentResult;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.SelectedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

@Repository
public class JdbcRecommendationSessionRepository implements RecommendationSessionRepository {

    private static final String PUBLIC_CONTRACT_VERSION = "recommendation-public-v1";
    private static final String FALLBACK_VERSION = "fallback-v1";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRecommendationSessionRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID createSession(UUID userId, RecommendationRequestContext request, Map<String, Object> requestSnapshot, UUID correlationId) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO recommendation_session (
                    id, user_id, group_id, parent_session_id, status, meal_type, max_budget, currency, area,
                    latitude, longitude, max_distance_km, mood, requested_for, request_context,
                    public_contract_version, model_status, fallback_status, correlation_id
                )
                VALUES (
                    :id, :userId, :groupId, :parentSessionId, 'CREATED', :mealType, :maxBudget, :currency, :area,
                    :latitude, :longitude, :maxDistanceKm, :mood, :requestedFor, CAST(:requestContext AS jsonb),
                    :publicContractVersion, 'NOT_REQUESTED', 'NOT_STARTED', :correlationId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", sessionId)
                        .addValue("userId", userId)
                        .addValue("groupId", request.groupId())
                        .addValue("parentSessionId", request.parentSessionId())
                        .addValue("mealType", request.mealType())
                        .addValue("maxBudget", request.maxBudget())
                        .addValue("currency", request.currency())
                        .addValue("area", request.area())
                        .addValue("latitude", request.latitude())
                        .addValue("longitude", request.longitude())
                        .addValue("maxDistanceKm", request.maxDistanceKm())
                        .addValue("mood", request.mood())
                        .addValue("requestedFor", request.requestedFor())
                        .addValue("requestContext", toJson(requestSnapshot))
                        .addValue("publicContractVersion", PUBLIC_CONTRACT_VERSION)
                        .addValue("correlationId", correlationId));
        return sessionId;
    }

    @Override
    public Map<UUID, UUID> insertEvaluations(UUID sessionId, List<EvaluatedCandidate> candidates, String featureSchemaVersion) {
        Map<UUID, UUID> candidateIdsByPlaceMeal = new LinkedHashMap<>();
        for (EvaluatedCandidate candidate : candidates) {
            UUID candidateId = UUID.randomUUID();
            candidateIdsByPlaceMeal.put(candidate.evidence().placeMealId(), candidateId);
            jdbcTemplate.update("""
                    INSERT INTO recommendation_candidate (
                        id, session_id, place_meal_id, eligibility_status, filter_code,
                        feature_schema_version, feature_snapshot, evidence_snapshot
                    )
                    VALUES (
                        :id, :sessionId, :placeMealId, :eligibilityStatus, :filterCode,
                        :featureSchemaVersion, CAST(:featureSnapshot AS jsonb), CAST(:evidenceSnapshot AS jsonb)
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", candidateId)
                            .addValue("sessionId", sessionId)
                            .addValue("placeMealId", candidate.evidence().placeMealId())
                            .addValue("eligibilityStatus", candidate.eligible() ? "ELIGIBLE" : "FILTERED")
                            .addValue("filterCode", candidate.filterCode() == null ? null : candidate.filterCode().name())
                            .addValue("featureSchemaVersion", featureSchemaVersion)
                            .addValue("featureSnapshot", toJson(featureSnapshot(candidate.evidence())))
                            .addValue("evidenceSnapshot", toJson(evidenceSnapshot(candidate.evidence()))));
        }
        return candidateIdsByPlaceMeal;
    }

    @Override
    public void markProcessing(UUID sessionId) {
        jdbcTemplate.update("""
                UPDATE recommendation_session
                SET status = 'PROCESSING',
                    started_at = CURRENT_TIMESTAMP
                WHERE id = :sessionId
                  AND status = 'CREATED'
                """,
                new MapSqlParameterSource("sessionId", sessionId));
    }

    @Override
    public void completeAgent(UUID userId, UUID sessionId, ValidatedAgentResult result) {
        lockProcessingSession(userId, sessionId);
        for (ValidatedAgentCandidate selectedCandidate : result.candidates()) {
            insertReasons(selectedCandidate.candidateId(), selectedCandidate);
            jdbcTemplate.update("""
                    UPDATE recommendation_candidate
                    SET eligibility_status = 'RETURNED',
                        candidate_type = :candidateType,
                        rank = :rank,
                        model_score = :modelScore
                    WHERE id = :candidateId
                      AND session_id = :sessionId
                      AND eligibility_status = 'ELIGIBLE'
                    """,
                    new MapSqlParameterSource()
                            .addValue("candidateId", selectedCandidate.candidateId())
                            .addValue("sessionId", sessionId)
                            .addValue("candidateType", selectedCandidate.recommendationType().name())
                            .addValue("rank", selectedCandidate.rank())
                            .addValue("modelScore", selectedCandidate.modelScore()));
        }
        jdbcTemplate.update("""
                UPDATE recommendation_session
                SET status = 'SUCCEEDED',
                    agent_contract_version = :agentContractVersion,
                    model_status = 'SUCCEEDED',
                    model_version = :modelVersion,
                    fallback_status = 'NOT_REQUIRED',
                    agent_trace_id = :agentTraceId,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId)
                        .addValue("agentContractVersion", result.agentContractVersion())
                        .addValue("modelVersion", result.modelVersion())
                        .addValue("agentTraceId", result.agentTraceId()));
    }

    @Override
    public void completeFallback(
            UUID userId,
            UUID sessionId,
            List<SelectedCandidate> selectedCandidates,
            AgentFailureCode failureCode,
            String agentContractVersion,
            String agentTraceId) {
        lockProcessingSession(userId, sessionId);
        for (SelectedCandidate selectedCandidate : selectedCandidates) {
            UUID candidateId = candidateId(sessionId, selectedCandidate.candidate().evidence().placeMealId());
            insertReasons(candidateId, selectedCandidate);
            jdbcTemplate.update("""
                    UPDATE recommendation_candidate
                    SET eligibility_status = 'RETURNED',
                        candidate_type = :candidateType,
                        rank = :rank,
                        fallback_score = :fallbackScore
                    WHERE id = :candidateId
                      AND eligibility_status = 'ELIGIBLE'
                    """,
                    new MapSqlParameterSource()
                            .addValue("candidateId", candidateId)
                            .addValue("candidateType", selectedCandidate.type().name())
                            .addValue("rank", selectedCandidate.rank())
                            .addValue("fallbackScore", selectedCandidate.fallbackScore()));
        }
        boolean empty = selectedCandidates.isEmpty();
        String modelStatus = failureCode == null ? "NOT_REQUESTED" : failureCode.modelStatus();
        jdbcTemplate.update("""
                UPDATE recommendation_session
                SET status = :status,
                    agent_contract_version = :agentContractVersion,
                    model_status = :modelStatus,
                    fallback_status = :fallbackStatus,
                    fallback_version = :fallbackVersion,
                    agent_trace_id = :agentTraceId,
                    failure_code = :failureCode,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId)
                        .addValue("status", empty ? "NO_VALID_CANDIDATE" : "FALLBACK_SUCCEEDED")
                        .addValue("agentContractVersion", agentContractVersion)
                        .addValue("modelStatus", modelStatus)
                        .addValue("fallbackStatus", empty ? "NO_VALID_CANDIDATE" : "SUCCEEDED")
                        .addValue("fallbackVersion", FALLBACK_VERSION)
                        .addValue("agentTraceId", agentTraceId)
                        .addValue("failureCode", failureCode == null ? null : failureCode.name()));
    }

    @Override
    public Optional<RecommendationResult> findResult(UUID userId, UUID sessionId, String traceId) {
        Optional<SessionRow> session = jdbcTemplate.query("""
                SELECT id, status, model_status, model_version, fallback_status, fallback_version,
                       created_at, completed_at
                FROM recommendation_session
                WHERE id = :sessionId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                this::sessionRow)
                .stream()
                .findFirst();
        if (session.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RecommendationResult(
                session.get().id(),
                traceId,
                session.get().status(),
                session.get().modelStatus(),
                session.get().modelVersion(),
                session.get().fallbackStatus(),
                session.get().fallbackVersion(),
                session.get().createdAt(),
                session.get().completedAt(),
                candidates(sessionId)));
    }

    @Override
    public List<RecommendationSessionSummary> history(UUID userId, int page, int size) {
        return jdbcTemplate.query("""
                SELECT rs.id, rs.group_id, rs.status, rs.fallback_status, rs.fallback_version,
                       count(rc.id) FILTER (WHERE rc.eligibility_status = 'RETURNED')::int AS returned_count,
                       rs.created_at, rs.completed_at
                FROM recommendation_session rs
                LEFT JOIN recommendation_candidate rc ON rc.session_id = rs.id
                WHERE rs.user_id = :userId
                GROUP BY rs.id
                ORDER BY rs.created_at DESC, rs.id DESC
                LIMIT :limit OFFSET :offset
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("limit", size)
                        .addValue("offset", page * size),
                this::summaryRow);
    }

    @Override
    public long historyCount(UUID userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM recommendation_session
                WHERE user_id = :userId
                """,
                new MapSqlParameterSource("userId", userId),
                Long.class);
        return count == null ? 0 : count;
    }

    private UUID candidateId(UUID sessionId, UUID placeMealId) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM recommendation_candidate
                WHERE session_id = :sessionId
                  AND place_meal_id = :placeMealId
                  AND eligibility_status = 'ELIGIBLE'
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("placeMealId", placeMealId),
                UUID.class);
    }

    private void lockProcessingSession(UUID userId, UUID sessionId) {
        List<UUID> locked = jdbcTemplate.query("""
                SELECT id
                FROM recommendation_session
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND status = 'PROCESSING'
                FOR UPDATE
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (locked.isEmpty()) {
            throw new IllegalStateException("Recommendation session is not completable.");
        }
    }

    private void insertReasons(UUID candidateId, SelectedCandidate selectedCandidate) {
        int sequence = 1;
        for (ReasonCode reasonCode : selectedCandidate.reasonCodes()) {
            jdbcTemplate.update("""
                    INSERT INTO candidate_reason (candidate_id, sequence_no, reason_code, evidence_json)
                    VALUES (:candidateId, :sequenceNo, :reasonCode, CAST(:evidenceJson AS jsonb))
                    """,
                    new MapSqlParameterSource()
                            .addValue("candidateId", candidateId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("reasonCode", reasonCode.name())
                            .addValue("evidenceJson", toJson(reasonEvidence(reasonCode, selectedCandidate))));
        }
    }

    private void insertReasons(UUID candidateId, ValidatedAgentCandidate selectedCandidate) {
        int sequence = 1;
        for (ReasonCode reasonCode : selectedCandidate.reasonCodes()) {
            jdbcTemplate.update("""
                    INSERT INTO candidate_reason (candidate_id, sequence_no, reason_code, evidence_json)
                    VALUES (:candidateId, :sequenceNo, :reasonCode, CAST(:evidenceJson AS jsonb))
                    """,
                    new MapSqlParameterSource()
                            .addValue("candidateId", candidateId)
                            .addValue("sequenceNo", sequence++)
                            .addValue("reasonCode", reasonCode.name())
                            .addValue("evidenceJson", toJson(reasonEvidence(reasonCode, selectedCandidate))));
        }
    }

    private List<RecommendationCandidateResult> candidates(UUID sessionId) {
        List<RecommendationCandidateResult> results = jdbcTemplate.query("""
                SELECT rc.id AS candidate_id,
                       rc.place_meal_id,
                       pm.meal_id,
                       m.name AS meal_name,
                       pm.place_id,
                       p.name AS place_name,
                       p.area,
                       pm.price,
                       pm.currency,
                       rc.candidate_type,
                       rc.rank,
                       rc.fallback_score
                FROM recommendation_candidate rc
                JOIN place_meal pm ON pm.id = rc.place_meal_id
                JOIN meal m ON m.id = pm.meal_id
                JOIN place p ON p.id = pm.place_id
                WHERE rc.session_id = :sessionId
                  AND rc.eligibility_status = 'RETURNED'
                ORDER BY rc.rank ASC
                """,
                new MapSqlParameterSource("sessionId", sessionId),
                this::candidateResultRow);
        Map<UUID, List<ReasonCode>> reasons = reasons(results.stream().map(RecommendationCandidateResult::candidateId).toList());
        return results.stream()
                .map(candidate -> new RecommendationCandidateResult(
                        candidate.candidateId(),
                        candidate.placeMealId(),
                        candidate.mealId(),
                        candidate.mealName(),
                        candidate.placeId(),
                        candidate.placeName(),
                        candidate.area(),
                        candidate.price(),
                        candidate.recommendationType(),
                        candidate.rank(),
                        reasons.getOrDefault(candidate.candidateId(), List.of()),
                        candidate.explanation(),
                        candidate.fallbackScore()))
                .toList();
    }

    private Map<UUID, List<ReasonCode>> reasons(List<UUID> candidateIds) {
        Map<UUID, List<ReasonCode>> result = new LinkedHashMap<>();
        candidateIds.forEach(candidateId -> result.put(candidateId, new ArrayList<>()));
        if (candidateIds.isEmpty()) {
            return result;
        }
        jdbcTemplate.query("""
                SELECT candidate_id, reason_code
                FROM candidate_reason
                WHERE candidate_id IN (:candidateIds)
                ORDER BY candidate_id, sequence_no
                """,
                new MapSqlParameterSource("candidateIds", candidateIds),
                (rs, rowNum) -> {
                    UUID candidateId = rs.getObject("candidate_id", UUID.class);
                    result.computeIfAbsent(candidateId, ignored -> new ArrayList<>())
                            .add(ReasonCode.valueOf(rs.getString("reason_code")));
                    return null;
                });
        return result;
    }

    private RecommendationCandidateResult candidateResultRow(ResultSet rs, int rowNum) throws SQLException {
        String type = rs.getString("candidate_type");
        return new RecommendationCandidateResult(
                rs.getObject("candidate_id", UUID.class),
                rs.getObject("place_meal_id", UUID.class),
                rs.getObject("meal_id", UUID.class),
                rs.getString("meal_name"),
                rs.getObject("place_id", UUID.class),
                rs.getString("place_name"),
                rs.getString("area"),
                new MoneyAmount(rs.getBigDecimal("price"), rs.getString("currency").trim()),
                RecommendationType.valueOf(type),
                rs.getInt("rank"),
                List.of(),
                explanationFromSnapshot(rs.getObject("candidate_id", UUID.class)),
                rs.getBigDecimal("fallback_score"));
    }

    private String explanationFromSnapshot(UUID candidateId) {
        return jdbcTemplate.query("""
                SELECT evidence_json ->> 'explanation' AS explanation
                FROM candidate_reason
                WHERE candidate_id = :candidateId
                ORDER BY sequence_no
                LIMIT 1
                """,
                new MapSqlParameterSource("candidateId", candidateId),
                (rs, rowNum) -> rs.getString("explanation"))
                .stream()
                .findFirst()
                .orElse("");
    }

    private SessionRow sessionRow(ResultSet rs, int rowNum) throws SQLException {
        return new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getString("model_status"),
                rs.getString("model_version"),
                rs.getString("fallback_status"),
                rs.getString("fallback_version"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private RecommendationSessionSummary summaryRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecommendationSessionSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getString("status"),
                rs.getString("fallback_status"),
                rs.getString("fallback_version"),
                rs.getInt("returned_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private Map<String, Object> evidenceSnapshot(CandidateEvidence evidence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("placeMealId", evidence.placeMealId());
        snapshot.put("mealId", evidence.mealId());
        snapshot.put("mealName", evidence.mealName());
        snapshot.put("mealType", evidence.mealType());
        snapshot.put("cuisineCode", evidence.cuisineCode());
        snapshot.put("placeId", evidence.placeId());
        snapshot.put("placeName", evidence.placeName());
        snapshot.put("area", evidence.area());
        snapshot.put("price", evidence.price());
        snapshot.put("spiceLevel", evidence.spiceLevel());
        snapshot.put("available", evidence.available());
        snapshot.put("cleanliness", evidence.cleanliness());
        snapshot.put("dietaryTagCodes", evidence.dietaryTagCodes());
        snapshot.put("allergenCodes", evidence.allergenCodes());
        snapshot.put("wantToTry", evidence.wantToTry());
        snapshot.put("personalRecordCount", evidence.personalRecordCount());
        snapshot.put("personalAverageRating", evidence.personalAverageRating());
        snapshot.put("lastPersonalRecordAt", evidence.lastPersonalRecordAt());
        snapshot.put("groupRecordCount", evidence.groupRecordCount());
        snapshot.put("groupAverageRating", evidence.groupAverageRating());
        snapshot.put("lastGroupRecordAt", evidence.lastGroupRecordAt());
        snapshot.put("distanceKm", evidence.distanceKm());
        return snapshot;
    }

    private Map<String, Object> featureSnapshot(CandidateEvidence evidence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mealType", evidence.mealType());
        snapshot.put("cuisineCode", evidence.cuisineCode());
        snapshot.put("area", evidence.area());
        snapshot.put("priceAmount", evidence.price() == null ? null : evidence.price().amount());
        snapshot.put("currency", evidence.price() == null ? null : evidence.price().currency());
        snapshot.put("spiceLevel", evidence.spiceLevel());
        snapshot.put("available", evidence.available());
        snapshot.put("cleanlinessScore", evidence.cleanliness() == null ? null : evidence.cleanliness().score());
        snapshot.put("dietaryTagCodes", evidence.dietaryTagCodes());
        snapshot.put("allergenCodes", evidence.allergenCodes());
        snapshot.put("wantToTry", evidence.wantToTry());
        snapshot.put("personalRecordCount", evidence.personalRecordCount());
        snapshot.put("personalAverageRating", evidence.personalAverageRating());
        snapshot.put("groupRecordCount", evidence.groupRecordCount());
        snapshot.put("groupAverageRating", evidence.groupAverageRating());
        snapshot.put("distanceKm", evidence.distanceKm());
        return snapshot;
    }

    private Map<String, Object> reasonEvidence(ReasonCode reasonCode, SelectedCandidate selectedCandidate) {
        CandidateEvidence evidence = selectedCandidate.candidate().evidence();
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("reasonCode", reasonCode.name());
        reason.put("candidateType", selectedCandidate.type().name());
        reason.put("placeMealId", evidence.placeMealId());
        reason.put("mealId", evidence.mealId());
        reason.put("placeId", evidence.placeId());
        reason.put("explanation", selectedCandidate.explanation());
        if (reasonCode == ReasonCode.TRUSTED_GROUP_RATING) {
            reason.put("groupRecordCount", evidence.groupRecordCount());
            reason.put("groupAverageRating", evidence.groupAverageRating());
        }
        if (reasonCode == ReasonCode.WANT_TO_TRY) {
            reason.put("wantToTry", evidence.wantToTry());
        }
        if (reasonCode == ReasonCode.NEARBY) {
            reason.put("distanceKm", evidence.distanceKm());
        }
        return reason;
    }

    private Map<String, Object> reasonEvidence(ReasonCode reasonCode, ValidatedAgentCandidate selectedCandidate) {
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("reasonCode", reasonCode.name());
        reason.put("candidateType", selectedCandidate.recommendationType().name());
        reason.put("candidateId", selectedCandidate.candidateId());
        reason.put("explanation", selectedCandidate.explanation());
        reason.put("source", "AGENT");
        return reason;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise recommendation persistence payload.", exception);
        }
    }

    private record SessionRow(
            UUID id,
            String status,
            String modelStatus,
            String modelVersion,
            String fallbackStatus,
            String fallbackVersion,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt) {
    }
}
