package com.foodmind.foodmindbackend.chat.infrastructure.persistence;

import com.foodmind.foodmindbackend.chat.application.port.ChatMessageContextQuery;
import com.foodmind.foodmindbackend.chat.domain.ChatMessage;
import com.foodmind.foodmindbackend.chat.domain.ChatMessageSource;
import com.foodmind.foodmindbackend.chat.domain.ChatResponseStatus;
import com.foodmind.foodmindbackend.chat.domain.ChatRoute;
import com.foodmind.foodmindbackend.chat.domain.ChatSourceType;
import com.foodmind.foodmindbackend.chat.domain.agent.ChatConversationTurn;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

@Repository
public class JdbcChatMessageContextQuery implements ChatMessageContextQuery {

    private static final int MAX_TURN_LENGTH = 2000;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcChatMessageContextQuery(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ChatConversationTurn> findRecentTurns(
            UUID userId,
            UUID sessionId,
            UUID beforeMessageId,
            int limit) {
        Boundary boundary = beforeMessageId == null ? null : findBoundary(userId, sessionId, beforeMessageId);
        List<ChatConversationTurn> descending = jdbcTemplate.query("""
                SELECT message.role, message.content
                FROM chat_message message
                JOIN chat_session session ON session.id = message.session_id
                WHERE message.session_id = :sessionId
                  AND session.user_id = :userId
                  AND (message.role = 'USER' OR message.response_status <> 'FAILED')
                  AND (
                    CAST(:beforeCreatedAt AS timestamptz) IS NULL
                    OR message.created_at < CAST(:beforeCreatedAt AS timestamptz)
                    OR (
                      message.created_at = CAST(:beforeCreatedAt AS timestamptz)
                      AND message.id < CAST(:beforeMessageId AS uuid)
                    )
                  )
                ORDER BY message.created_at DESC, message.id DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId)
                        .addValue("beforeCreatedAt", boundary == null ? null : boundary.createdAt())
                        .addValue("beforeMessageId", boundary == null ? null : boundary.id())
                        .addValue("limit", Math.max(1, Math.min(limit, 20))),
                (rs, rowNum) -> new ChatConversationTurn(
                        rs.getString("role"),
                        boundedTurn(rs.getString("content"))));
        List<ChatConversationTurn> chronological = new ArrayList<>(descending);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    public Optional<ChatMessage> findOwnedMessage(UUID userId, UUID sessionId, UUID messageId) {
        return jdbcTemplate.query("""
                SELECT message.id, message.session_id, message.role, message.content, message.route,
                       message.response_status, message.correlation_id, message.agent_trace_id, message.created_at,
                       message.suggested_questions, message.suggested_destinations
                FROM chat_message message
                JOIN chat_session session ON session.id = message.session_id
                WHERE message.id = :messageId
                  AND message.session_id = :sessionId
                  AND session.user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("messageId", messageId)
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                this::messageRow)
                .stream()
                .findFirst()
                .map(message -> new ChatMessage(
                        message.id(),
                        message.sessionId(),
                        message.role(),
                        message.content(),
                        message.route(),
                        message.responseStatus(),
                        message.correlationId(),
                        message.agentTraceId(),
                        message.createdAt(),
                        sources(sessionId, messageId),
                        message.suggestedQuestions(),
                        message.suggestedDestinations()));
    }

    private Boundary findBoundary(UUID userId, UUID sessionId, UUID messageId) {
        return jdbcTemplate.query("""
                SELECT message.id, message.created_at
                FROM chat_message message
                JOIN chat_session session ON session.id = message.session_id
                WHERE message.id = :messageId
                  AND message.session_id = :sessionId
                  AND session.user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("messageId", messageId)
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId),
                (rs, rowNum) -> new Boundary(
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("id", UUID.class)))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<ChatMessageSource> sources(UUID sessionId, UUID messageId) {
        return jdbcTemplate.query("""
                SELECT source.reference_id, source.sequence_no, source.grounding_metadata,
                       reference.source_type,
                       reference.food_record_id,
                       reference.food_product_id,
                       reference.place_id,
                       coalesce(record.meal_name_snapshot, product.name, place.name) AS title,
                       case reference.source_type
                           when 'FOOD_RECORD' then left(coalesce(nullif(concat_ws('; ',
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
                           ), ''), record.meal_name_snapshot), 4000)
                           when 'FOOD_PRODUCT' then left(coalesce(nullif(concat_ws('; ',
                               case when nullif(btrim(product.brand), '') is not null
                                   then 'brand ' || btrim(product.brand) end,
                               case when nullif(btrim(product.description), '') is not null
                                   then 'description: ' || btrim(product.description) end,
                               case when product.price is not null
                                   then concat_ws(' ', 'price', product.price::text, nullif(btrim(product.currency), '')) end,
                               case when nullif(btrim(product_place.name), '') is not null
                                   then 'place ' || btrim(product_place.name) end
                           ), ''), product.name), 4000)
                           when 'PLACE' then left(coalesce(nullif(concat_ws('; ',
                               case when nullif(btrim(place.place_type), '') is not null
                                   then 'type ' || btrim(place.place_type) end,
                               case when nullif(btrim(place.area), '') is not null
                                   then 'area ' || btrim(place.area) end,
                               case when nullif(btrim(place.address_text), '') is not null
                                   then 'address ' || btrim(place.address_text) end,
                               case when place.price_band is not null
                                   then 'price band ' || place.price_band::text end
                           ), ''), place.name), 4000)
                       end AS snippet
                FROM chat_message_source source
                JOIN chat_reference reference ON reference.id = source.reference_id
                LEFT JOIN food_record record ON record.id = reference.food_record_id
                LEFT JOIN food_product product ON product.id = reference.food_product_id
                LEFT JOIN place product_place ON product_place.id = product.place_id
                LEFT JOIN place place ON place.id = reference.place_id
                WHERE source.session_id = :sessionId
                  AND source.message_id = :messageId
                ORDER BY source.sequence_no ASC
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("messageId", messageId),
                this::sourceRow);
    }

    private ChatMessage messageRow(ResultSet rs, int rowNum) throws SQLException {
        String route = rs.getString("route");
        String responseStatus = rs.getString("response_status");
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getString("role"),
                rs.getString("content"),
                route == null ? null : ChatRoute.valueOf(route),
                responseStatus == null ? null : ChatResponseStatus.valueOf(responseStatus),
                rs.getObject("correlation_id", UUID.class),
                rs.getString("agent_trace_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                List.of(),
                jsonStringList(rs.getString("suggested_questions")),
                jsonStringList(rs.getString("suggested_destinations")));
    }

    private ChatMessageSource sourceRow(ResultSet rs, int rowNum) throws SQLException {
        ChatSourceType sourceType = ChatSourceType.valueOf(rs.getString("source_type"));
        UUID sourceId = switch (sourceType) {
            case FOOD_RECORD -> rs.getObject("food_record_id", UUID.class);
            case FOOD_PRODUCT -> rs.getObject("food_product_id", UUID.class);
            case PLACE -> rs.getObject("place_id", UUID.class);
        };
        return new ChatMessageSource(
                rs.getObject("reference_id", UUID.class),
                sourceType,
                sourceId,
                rs.getInt("sequence_no"),
                rs.getString("title"),
                rs.getString("snippet"),
                jsonMap(rs.getString("grounding_metadata")));
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

    private String boundedTurn(String content) {
        return content.length() <= MAX_TURN_LENGTH
                ? content
                : content.substring(0, MAX_TURN_LENGTH).stripTrailing();
    }

    private record Boundary(OffsetDateTime createdAt, UUID id) {
    }
}
