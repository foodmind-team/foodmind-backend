package com.foodmind.foodmindbackend.chat.infrastructure.persistence;

import com.foodmind.foodmindbackend.chat.application.port.ChatReferenceQuery;
import com.foodmind.foodmindbackend.chat.application.port.ChatRepository;
import com.foodmind.foodmindbackend.chat.domain.ChatCursor;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.ChatMessageSource;
import com.foodmind.foodmindbackend.chat.domain.ChatPage;
import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import com.foodmind.foodmindbackend.chat.domain.ChatReferenceOrigin;
import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatSession;
import com.foodmind.foodmindbackend.chat.domain.ChatSourcePointer;
import com.foodmind.foodmindbackend.chat.domain.ChatSourceResolution;
import com.foodmind.foodmindbackend.chat.domain.ChatSourceType;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatAgentSourceResult;
import com.foodmind.foodmindbackend.chat.domain.agent.ValidatedChatAgentResult;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Repository
public class ChatQueryAdapter implements ChatRepository, ChatReferenceQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatSession createSession(UUID userId, String title) {
        UUID sessionId = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                INSERT INTO chat_session (id, user_id, title)
                VALUES (:id, :userId, :title)
                RETURNING id, title, status, created_at, updated_at
                """,
                new MapSqlParameterSource()
                        .addValue("id", sessionId)
                        .addValue("userId", userId)
                        .addValue("title", title),
                this::sessionRow);
    }

    @Override
    public ChatPage<ChatSession> findOwnedSessions(UUID userId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<ChatSession> rows = jdbcTemplate.query("""
                SELECT id, title, status, created_at, updated_at
                FROM chat_session
                WHERE user_id = :userId
                  AND status = 'ACTIVE'
                ORDER BY updated_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("limit", safeSize + 1)
                        .addValue("offset", Math.max(0, page) * safeSize),
                this::sessionRow);
        return page(rows, safeSize, null);
    }

    @Override
    public Optional<ChatSession> findOwnedSession(UUID userId, UUID sessionId) {
        return jdbcTemplate.query("""
                SELECT id, title, status, created_at, updated_at
                FROM chat_session
                WHERE id = :sessionId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                this::sessionRow)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ChatSession> findActiveOwnedSession(UUID userId, UUID sessionId) {
        return jdbcTemplate.query("""
                SELECT id, title, status, created_at, updated_at
                FROM chat_session
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                this::sessionRow)
                .stream()
                .findFirst();
    }

    @Override
    public void archiveOwnedSession(UUID userId, UUID sessionId) {
        int updated = jdbcTemplate.update("""
                UPDATE chat_session
                SET status = 'ARCHIVED'
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId));
        if (updated == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public UUID insertUserMessage(UUID userId, UUID sessionId, String content, UUID correlationId) {
        lockActiveSession(userId, sessionId);
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO chat_message (id, session_id, role, content, correlation_id)
                VALUES (:id, :sessionId, 'USER', :content, :correlationId)
                """,
                new MapSqlParameterSource()
                        .addValue("id", messageId)
                        .addValue("sessionId", sessionId)
                        .addValue("content", content)
                        .addValue("correlationId", correlationId));
        touchSession(userId, sessionId);
        return messageId;
    }

    @Override
    public ChatMessage insertAssistantMessage(
            UUID userId,
            UUID sessionId,
            UUID userMessageId,
            ValidatedChatAgentResult result) {
        lockActiveSession(userId, sessionId);
        UUID assistantMessageId = UUID.randomUUID();
        UUID correlationId = messageCorrelation(sessionId, userMessageId);
        jdbcTemplate.update("""
                INSERT INTO chat_message (
                    id, session_id, role, content, response_status, correlation_id, agent_trace_id,
                    suggested_questions, suggested_destinations
                )
                VALUES (
                    :id, :sessionId, 'ASSISTANT', :content, :responseStatus, :correlationId, :agentTraceId,
                    CAST(:suggestedQuestions AS jsonb), CAST(:suggestedDestinations AS jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", assistantMessageId)
                        .addValue("sessionId", sessionId)
                        .addValue("content", result.answer())
                        .addValue("responseStatus", result.responseStatus().name())
                        .addValue("correlationId", correlationId)
                        .addValue("agentTraceId", result.agentTraceId())
                        .addValue("suggestedQuestions", toJson(result.suggestedQuestions()))
                        .addValue("suggestedDestinations", toJson(result.suggestedDestinations())));
        for (ChatAgentSourceResult source : result.sources()) {
            ChatReference reference = findReferenceBySource(sessionId, source.sourceType(), source.sourceId())
                    .orElseGet(() -> insertMessageReference(sessionId, assistantMessageId, source.sourceType(), source.sourceId()));
            jdbcTemplate.update("""
                    INSERT INTO chat_message_source (
                        session_id, message_id, reference_id, sequence_no, grounding_metadata
                    )
                    VALUES (
                        :sessionId, :messageId, :referenceId, :sequenceNo, CAST(:groundingMetadata AS jsonb)
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("sessionId", sessionId)
                            .addValue("messageId", assistantMessageId)
                            .addValue("referenceId", reference.id())
                            .addValue("sequenceNo", source.sequenceNo())
                            .addValue("groundingMetadata", toJson(source.groundingMetadata() == null ? Map.of() : source.groundingMetadata())));
        }
        touchSession(userId, sessionId);
        return findMessage(sessionId, assistantMessageId).orElseThrow();
    }

    @Override
    public ChatMessage insertFailedAssistantMessage(UUID userId, UUID sessionId, UUID userMessageId, String traceId) {
        lockActiveSession(userId, sessionId);
        UUID assistantMessageId = UUID.randomUUID();
        UUID correlationId = messageCorrelation(sessionId, userMessageId);
        jdbcTemplate.update("""
                INSERT INTO chat_message (
                    id, session_id, role, content, response_status, correlation_id, agent_trace_id
                )
                VALUES (
                    :id, :sessionId, 'ASSISTANT', :content, 'FAILED', :correlationId, :agentTraceId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", assistantMessageId)
                        .addValue("sessionId", sessionId)
                        .addValue("content", "I could not produce a grounded answer from authorised FoodMind sources.")
                        .addValue("correlationId", correlationId)
                        .addValue("agentTraceId", traceId));
        touchSession(userId, sessionId);
        return findMessage(sessionId, assistantMessageId).orElseThrow();
    }

    @Override
    public ChatPage<ChatMessage> findOwnedMessages(UUID userId, UUID sessionId, int size, ChatCursor after) {
        findOwnedSession(userId, sessionId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("limit", size + 1)
                .addValue("afterCreatedAt", after == null ? null : after.createdAt())
                .addValue("afterId", after == null ? null : after.id());
        List<ChatMessage> rows = jdbcTemplate.query("""
                SELECT id, session_id, role, content, response_status, correlation_id, agent_trace_id, created_at,
                       suggested_questions, suggested_destinations
                FROM chat_message
                WHERE session_id = :sessionId
                  AND (
                    CAST(:afterCreatedAt AS timestamptz) IS NULL
                    OR created_at > CAST(:afterCreatedAt AS timestamptz)
                    OR (
                      created_at = CAST(:afterCreatedAt AS timestamptz)
                      AND id > CAST(:afterId AS uuid)
                    )
                  )
                ORDER BY created_at ASC, id ASC
                LIMIT :limit
                """,
                params,
                this::messageRow);
        List<ChatMessage> hydrated = rows.stream()
                .map(message -> new ChatMessage(
                        message.id(),
                        message.sessionId(),
                        message.role(),
                        message.content(),
                        message.responseStatus(),
                        message.correlationId(),
                        message.agentTraceId(),
                        message.createdAt(),
                        sources(message.sessionId(), message.id()),
                        message.suggestedQuestions(),
                        message.suggestedDestinations()))
                .toList();
        String nextCursor = hydrated.size() > size ? new ChatCursor(hydrated.get(size - 1).createdAt(), hydrated.get(size - 1).id()).encode() : null;
        return new ChatPage<>(new ArrayList<>(hydrated.subList(0, Math.min(hydrated.size(), size))), nextCursor, nextCursor != null);
    }

    @Override
    public List<ChatReference> findSessionReferences(UUID userId, UUID sessionId) {
        findOwnedSession(userId, sessionId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return references(sessionId).stream()
                .filter(reference -> reference.origin() == ChatReferenceOrigin.USER_SHARED)
                .toList();
    }

    @Override
    public ChatReference upsertUserReference(UUID userId, UUID sessionId, ChatSourcePointer source) {
        lockActiveSession(userId, sessionId);
        Optional<ChatReference> existing = findReferenceBySource(sessionId, source.sourceType(), source.sourceId());
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE chat_reference
                    SET origin = 'USER_SHARED', introduced_by_message_id = NULL
                    WHERE id = :referenceId
                    """,
                    new MapSqlParameterSource("referenceId", existing.get().id()));
            touchSession(userId, sessionId);
            return findReference(existing.get().id()).orElseThrow();
        }
        UUID referenceId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO chat_reference (
                    id, session_id, origin, source_type, food_record_id, food_product_id, place_id
                )
                VALUES (
                    :id, :sessionId, 'USER_SHARED', :sourceType, :foodRecordId, :foodProductId, :placeId
                )
                """,
                referenceParams(referenceId, sessionId, null, ChatReferenceOrigin.USER_SHARED, source.sourceType(), source.sourceId()));
        touchSession(userId, sessionId);
        return findReference(referenceId).orElseThrow();
    }

    @Override
    public Optional<ChatSourceResolution> resolveAuthorised(UUID actorUserId, ChatSourcePointer source) {
        return switch (source.sourceType()) {
            case FOOD_RECORD -> resolveFoodRecord(actorUserId, source.sourceId());
            case FOOD_PRODUCT -> resolveProduct(source.sourceId());
            case PLACE -> resolvePlace(source.sourceId());
        };
    }

    @Override
    public List<ChatReference> resolveSessionReferences(UUID actorUserId, UUID sessionId, List<UUID> referenceIds) {
        List<ChatReference> references = references(sessionId).stream()
                .filter(reference -> referenceIds == null || referenceIds.isEmpty() || referenceIds.contains(reference.id()))
                .toList();
        return references.stream()
                .map(reference -> resolveAuthorised(actorUserId, reference.pointer())
                        .map(resolution -> new ChatReference(
                                reference.id(),
                                reference.sessionId(),
                                reference.origin(),
                                reference.introducedByMessageId(),
                                reference.sourceType(),
                                reference.sourceId(),
                                true,
                                resolution.title(),
                                resolution.snippet(),
                                reference.createdAt()))
                        .orElse(new ChatReference(
                                reference.id(),
                                reference.sessionId(),
                                reference.origin(),
                                reference.introducedByMessageId(),
                                reference.sourceType(),
                                reference.sourceId(),
                                false,
                                null,
                                null,
                                reference.createdAt())))
                .toList();
    }

    private Optional<ChatSourceResolution> resolveFoodRecord(UUID actorUserId, UUID sourceId) {
        return jdbcTemplate.query("""
                SELECT 'FOOD_RECORD' AS source_type,
                       record.id AS source_id,
                       record.meal_name_snapshot AS title,
                       left(coalesce(nullif(concat_ws('; ',
                           case when nullif(btrim(record.place_name_snapshot), '') is not null
                               then 'at ' || btrim(record.place_name_snapshot) end,
                           case when record.occurred_at is not null
                               then 'occurred at ' || record.occurred_at::text end,
                           case when record.price is not null
                               then concat_ws(' ', 'price', record.price::text, nullif(btrim(record.currency), '')) end,
                           case when record.rating is not null
                               then 'rating ' || record.rating::text || '/5' end,
                           case when record.would_eat_again is not null
                               then 'would_eat_again=' || record.would_eat_again::text end,
                           case when nullif(btrim(record.comment), '') is not null
                               then 'comment: ' || btrim(record.comment) end
                       ), ''), record.meal_name_snapshot), 4000) AS snippet
                FROM food_record record
                LEFT JOIN trusted_group trusted_group ON trusted_group.id = record.group_id
                WHERE record.id = :sourceId
                  AND record.deleted_at IS NULL
                  AND (
                    record.owner_user_id = :actorUserId
                    OR (
                      record.visibility = 'GROUP'
                      AND trusted_group.status = 'ACTIVE'
                      AND EXISTS (
                        SELECT 1
                        FROM group_membership membership
                        WHERE membership.group_id = record.group_id
                          AND membership.user_id = :actorUserId
                          AND membership.status = 'ACTIVE'
                      )
                    )
                  )
                """,
                new MapSqlParameterSource()
                        .addValue("sourceId", sourceId)
                        .addValue("actorUserId", actorUserId),
                this::resolutionRow)
                .stream()
                .findFirst();
    }

    private Optional<ChatSourceResolution> resolveProduct(UUID sourceId) {
        return jdbcTemplate.query("""
                SELECT 'FOOD_PRODUCT' AS source_type,
                       product.id AS source_id,
                       product.name AS title,
                       left(coalesce(nullif(concat_ws('; ',
                           case when nullif(btrim(product.brand), '') is not null
                               then 'brand ' || btrim(product.brand) end,
                           case when nullif(btrim(product.description), '') is not null
                               then 'description: ' || btrim(product.description) end,
                           case when product.price is not null
                               then concat_ws(' ', 'price', product.price::text, nullif(btrim(product.currency), '')) end,
                           case when nullif(btrim(place.name), '') is not null
                               then 'place ' || btrim(place.name) end
                       ), ''), product.name), 4000) AS snippet
                FROM food_product product
                LEFT JOIN place place ON place.id = product.place_id
                WHERE product.id = :sourceId
                  AND product.curation_status = 'ACTIVE'
                """,
                new MapSqlParameterSource("sourceId", sourceId),
                this::resolutionRow)
                .stream()
                .findFirst();
    }

    private Optional<ChatSourceResolution> resolvePlace(UUID sourceId) {
        return jdbcTemplate.query("""
                SELECT 'PLACE' AS source_type,
                       place.id AS source_id,
                       place.name AS title,
                       left(coalesce(nullif(concat_ws('; ',
                           case when nullif(btrim(place.place_type), '') is not null
                               then 'type ' || btrim(place.place_type) end,
                           case when nullif(btrim(place.area), '') is not null
                               then 'area ' || btrim(place.area) end,
                           case when nullif(btrim(place.address_text), '') is not null
                               then 'address ' || btrim(place.address_text) end,
                           case when place.price_band is not null
                               then 'price band ' || place.price_band::text end
                       ), ''), place.name), 4000) AS snippet
                FROM place
                WHERE place.id = :sourceId
                  AND place.curation_status = 'ACTIVE'
                """,
                new MapSqlParameterSource("sourceId", sourceId),
                this::resolutionRow)
                .stream()
                .findFirst();
    }

    private ChatReference insertMessageReference(UUID sessionId, UUID messageId, ChatSourceType sourceType, UUID sourceId) {
        UUID referenceId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO chat_reference (
                    id, session_id, origin, introduced_by_message_id, source_type, food_record_id, food_product_id, place_id
                )
                VALUES (
                    :id, :sessionId, 'MESSAGE_INTRODUCED', :messageId, :sourceType, :foodRecordId, :foodProductId, :placeId
                )
                """,
                referenceParams(referenceId, sessionId, messageId, ChatReferenceOrigin.MESSAGE_INTRODUCED, sourceType, sourceId));
        return findReference(referenceId).orElseThrow();
    }

    private List<ChatReference> references(UUID sessionId) {
        return jdbcTemplate.query("""
                SELECT id, session_id, origin, introduced_by_message_id, source_type,
                       food_record_id, food_product_id, place_id, created_at
                FROM chat_reference
                WHERE session_id = :sessionId
                ORDER BY created_at ASC, id ASC
                """,
                new MapSqlParameterSource("sessionId", sessionId),
                this::referenceRow);
    }

    private Optional<ChatReference> findReference(UUID referenceId) {
        return jdbcTemplate.query("""
                SELECT id, session_id, origin, introduced_by_message_id, source_type,
                       food_record_id, food_product_id, place_id, created_at
                FROM chat_reference
                WHERE id = :referenceId
                """,
                new MapSqlParameterSource("referenceId", referenceId),
                this::referenceRow)
                .stream()
                .findFirst();
    }

    private Optional<ChatReference> findReferenceBySource(UUID sessionId, ChatSourceType sourceType, UUID sourceId) {
        String column = sourceColumn(sourceType);
        return jdbcTemplate.query("""
                SELECT id, session_id, origin, introduced_by_message_id, source_type,
                       food_record_id, food_product_id, place_id, created_at
                FROM chat_reference
                WHERE session_id = :sessionId
                  AND source_type = :sourceType
                  AND %s = :sourceId
                """.formatted(column),
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("sourceType", sourceType.name())
                        .addValue("sourceId", sourceId),
                this::referenceRow)
                .stream()
                .findFirst();
    }

    private Optional<ChatMessage> findMessage(UUID sessionId, UUID messageId) {
        return jdbcTemplate.query("""
                SELECT id, session_id, role, content, response_status, correlation_id, agent_trace_id, created_at,
                       suggested_questions, suggested_destinations
                FROM chat_message
                WHERE session_id = :sessionId
                  AND id = :messageId
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("messageId", messageId),
                this::messageRow)
                .stream()
                .findFirst()
                .map(message -> new ChatMessage(
                        message.id(),
                        message.sessionId(),
                        message.role(),
                        message.content(),
                        message.responseStatus(),
                        message.correlationId(),
                        message.agentTraceId(),
                        message.createdAt(),
                        sources(sessionId, messageId),
                        message.suggestedQuestions(),
                        message.suggestedDestinations()));
    }

    private List<ChatMessageSource> sources(UUID sessionId, UUID messageId) {
        return jdbcTemplate.query("""
                SELECT cms.reference_id, cms.sequence_no, cms.grounding_metadata,
                       cr.source_type, cr.food_record_id, cr.food_product_id, cr.place_id
                FROM chat_message_source cms
                JOIN chat_reference cr ON cr.id = cms.reference_id
                WHERE cms.session_id = :sessionId
                  AND cms.message_id = :messageId
                ORDER BY cms.sequence_no ASC
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("messageId", messageId),
                this::sourceRow);
    }

    private void lockActiveSession(UUID userId, UUID sessionId) {
        List<UUID> locked = jdbcTemplate.query("""
                SELECT id
                FROM chat_session
                WHERE id = :sessionId
                  AND user_id = :userId
                  AND status = 'ACTIVE'
                FOR UPDATE
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (locked.isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void touchSession(UUID userId, UUID sessionId) {
        jdbcTemplate.update("""
                UPDATE chat_session
                SET updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = :sessionId
                  AND user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId));
    }

    private UUID messageCorrelation(UUID sessionId, UUID userMessageId) {
        return jdbcTemplate.queryForObject("""
                SELECT correlation_id
                FROM chat_message
                WHERE session_id = :sessionId
                  AND id = :messageId
                  AND role = 'USER'
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("messageId", userMessageId),
                UUID.class);
    }

    private MapSqlParameterSource referenceParams(
            UUID referenceId,
            UUID sessionId,
            UUID messageId,
            ChatReferenceOrigin origin,
            ChatSourceType sourceType,
            UUID sourceId) {
        return new MapSqlParameterSource()
                .addValue("id", referenceId)
                .addValue("sessionId", sessionId)
                .addValue("messageId", messageId)
                .addValue("origin", origin.name())
                .addValue("sourceType", sourceType.name())
                .addValue("foodRecordId", sourceType == ChatSourceType.FOOD_RECORD ? sourceId : null)
                .addValue("foodProductId", sourceType == ChatSourceType.FOOD_PRODUCT ? sourceId : null)
                .addValue("placeId", sourceType == ChatSourceType.PLACE ? sourceId : null);
    }

    private ChatSession sessionRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatSession(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private ChatMessage messageRow(ResultSet rs, int rowNum) throws SQLException {
        String responseStatus = rs.getString("response_status");
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getString("role"),
                rs.getString("content"),
                responseStatus == null ? null : ChatResponseStatus.valueOf(responseStatus),
                rs.getObject("correlation_id", UUID.class),
                rs.getString("agent_trace_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                List.of(),
                jsonStringList(rs.getString("suggested_questions")),
                jsonStringList(rs.getString("suggested_destinations")));
    }

    private ChatReference referenceRow(ResultSet rs, int rowNum) throws SQLException {
        ChatSourceType sourceType = ChatSourceType.valueOf(rs.getString("source_type"));
        return new ChatReference(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                ChatReferenceOrigin.valueOf(rs.getString("origin")),
                rs.getObject("introduced_by_message_id", UUID.class),
                sourceType,
                sourceId(rs, sourceType),
                true,
                null,
                null,
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private ChatSourceResolution resolutionRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatSourceResolution(
                ChatSourceType.valueOf(rs.getString("source_type")),
                rs.getObject("source_id", UUID.class),
                true,
                rs.getString("title"),
                rs.getString("snippet"));
    }

    private ChatMessageSource sourceRow(ResultSet rs, int rowNum) throws SQLException {
        ChatSourceType sourceType = ChatSourceType.valueOf(rs.getString("source_type"));
        UUID sourceId = sourceId(rs, sourceType);
        Optional<ChatSourceResolution> resolution = switch (sourceType) {
            case FOOD_RECORD -> Optional.empty();
            case FOOD_PRODUCT -> resolveProduct(sourceId);
            case PLACE -> resolvePlace(sourceId);
        };
        return new ChatMessageSource(
                rs.getObject("reference_id", UUID.class),
                sourceType,
                sourceId,
                rs.getInt("sequence_no"),
                resolution.map(ChatSourceResolution::title).orElse(null),
                resolution.map(ChatSourceResolution::snippet).orElse(null),
                jsonMap(rs.getString("grounding_metadata")));
    }

    private UUID sourceId(ResultSet rs, ChatSourceType sourceType) throws SQLException {
        return switch (sourceType) {
            case FOOD_RECORD -> rs.getObject("food_record_id", UUID.class);
            case FOOD_PRODUCT -> rs.getObject("food_product_id", UUID.class);
            case PLACE -> rs.getObject("place_id", UUID.class);
        };
    }

    private String sourceColumn(ChatSourceType sourceType) {
        return switch (sourceType) {
            case FOOD_RECORD -> "food_record_id";
            case FOOD_PRODUCT -> "food_product_id";
            case PLACE -> "place_id";
        };
    }

    private <T> ChatPage<T> page(List<T> rows, int size, String nextCursor) {
        boolean hasNext = rows.size() > size;
        List<T> items = hasNext ? new ArrayList<>(rows.subList(0, size)) : rows;
        return new ChatPage<>(items, nextCursor, hasNext);
    }

    private Map<String, Object> jsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException exception) {
            return Map.of();
        }
    }

    private List<String> jsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : List.copyOf(values);
        } catch (JacksonException | NullPointerException exception) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise chat grounding metadata.", exception);
        }
    }
}
