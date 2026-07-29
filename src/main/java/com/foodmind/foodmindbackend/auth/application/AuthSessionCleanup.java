package com.foodmind.foodmindbackend.auth.application;

import com.foodmind.foodmindbackend.auth.application.port.AuthSessionRepository;
import com.foodmind.foodmindbackend.common.security.SecurityProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Component
public class AuthSessionCleanup {

    private final AuthSessionRepository authSessionRepository;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public AuthSessionCleanup(
            AuthSessionRepository authSessionRepository,
            SecurityProperties securityProperties,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public int deleteOldRevokedSessions() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(securityProperties.getRefresh().getCleanupRetention());
        return authSessionRepository.deleteExpiredOrRevokedBefore(cutoff);
    }
}
