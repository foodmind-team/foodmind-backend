package com.foodmind.foodmindbackend.auth.api.response;

import com.foodmind.foodmindbackend.auth.application.AuthTokens;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record AuthTokenResponse(
        UUID userId,
        String accessToken,
        String tokenType,
        long expiresIn,
        OffsetDateTime expiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt,
        String csrfToken) {

    public static AuthTokenResponse from(AuthTokens tokens, String csrfToken) {
        return new AuthTokenResponse(
                tokens.userId(),
                tokens.accessToken(),
                "Bearer",
                tokens.expiresIn(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt(),
                csrfToken);
    }
}
