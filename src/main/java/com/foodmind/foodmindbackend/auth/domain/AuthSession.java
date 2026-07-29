package com.foodmind.foodmindbackend.auth.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record AuthSession(
        UUID id,
        UUID userId,
        UUID tokenFamilyId,
        String refreshTokenHash,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime rotatedAt,
        OffsetDateTime revokedAt,
        UUID replacedBySessionId,
        ClientType clientType,
        String deviceLabel) {

    public boolean activeAt(OffsetDateTime now) {
        return rotatedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
}
