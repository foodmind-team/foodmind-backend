package com.foodmind.foodmindbackend.common.idempotency;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 128;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IdempotencyService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String sha256Hex(String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM.", exception);
        }
    }

    @Transactional
    public IdempotencyRecord begin(UUID userId, String operation, String idempotencyKey, String requestHash) {
        String safeKey = validateKey(idempotencyKey);
        UUID id = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);
        jdbcTemplate.update("""
                INSERT INTO idempotency_record (
                    id, user_id, operation, idempotency_key, request_hash, state, expires_at
                )
                VALUES (:id, :userId, :operation, :idempotencyKey, :requestHash, 'IN_PROGRESS', :expiresAt)
                ON CONFLICT (user_id, operation, idempotency_key) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId)
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", safeKey)
                        .addValue("requestHash", requestHash)
                        .addValue("expiresAt", expiresAt));

        IdempotencyRecord record = jdbcTemplate.query("""
                SELECT id, state, request_hash, resource_id
                FROM idempotency_record
                WHERE user_id = :userId
                  AND operation = :operation
                  AND idempotency_key = :idempotencyKey
                FOR UPDATE
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", safeKey),
                (rs, rowNum) -> new IdempotencyRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("state"),
                        rs.getString("request_hash"),
                        rs.getObject("resource_id", UUID.class)))
                .stream()
                .findFirst()
                .orElseThrow();

        if (!record.requestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return record;
    }

    @Transactional
    public void complete(UUID recordId, UUID resourceId, int responseStatus, Object responseBody) {
        jdbcTemplate.update("""
                UPDATE idempotency_record
                SET state = 'COMPLETED',
                    resource_id = :resourceId,
                    response_status = :responseStatus,
                    response_body = CAST(:responseBody AS jsonb)
                WHERE id = :recordId
                """,
                new MapSqlParameterSource()
                        .addValue("recordId", recordId)
                        .addValue("resourceId", resourceId)
                        .addValue("responseStatus", responseStatus)
                        .addValue("responseBody", responseBody));
    }

    private String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key is required and must be at most 128 characters.");
        }
        String trimmed = idempotencyKey.trim();
        if (!trimmed.equals(idempotencyKey)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key must not contain leading or trailing whitespace.");
        }
        return trimmed;
    }
}
