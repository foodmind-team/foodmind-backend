package com.foodmind.foodmindbackend.auth.application;

import com.foodmind.foodmindbackend.auth.application.port.AuthSessionRepository;
import com.foodmind.foodmindbackend.auth.domain.RefreshToken;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Service
public class LogoutSession {

    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public LogoutSession(AuthSessionRepository authSessionRepository, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = RefreshToken.fromRaw(rawRefreshToken).hash();
        authSessionRepository.findByRefreshTokenHashForUpdate(hash)
                .ifPresent(session -> authSessionRepository.revoke(session.id(), OffsetDateTime.now(clock)));
    }

    @Transactional
    public void logoutAll(UUID userId) {
        authSessionRepository.revokeActiveForUser(userId, OffsetDateTime.now(clock));
    }
}
