package com.foodmind.foodmindbackend.auth.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record AuthTokens(
        UUID userId,
        String accessToken,
        long expiresIn,
        OffsetDateTime accessTokenExpiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt,
        UUID sessionId,
        UUID tokenFamilyId) {
}
