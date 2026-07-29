package com.foodmind.foodmindbackend.auth.application.port;

import com.foodmind.foodmindbackend.auth.domain.AuthSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findByRefreshTokenHashForUpdate(String refreshTokenHash);

    void rotate(AuthSession predecessor, AuthSession successor, OffsetDateTime rotatedAt);

    void revoke(UUID sessionId, OffsetDateTime revokedAt);

    void revokeActiveForUser(UUID userId, OffsetDateTime revokedAt);

    void revokeFamily(UUID tokenFamilyId, OffsetDateTime revokedAt);

    int deleteExpiredOrRevokedBefore(OffsetDateTime cutoff);
}
