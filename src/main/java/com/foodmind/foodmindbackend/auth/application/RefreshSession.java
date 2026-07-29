package com.foodmind.foodmindbackend.auth.application;

import com.foodmind.foodmindbackend.auth.application.port.AuthSessionRepository;
import com.foodmind.foodmindbackend.auth.domain.AuthSession;
import com.foodmind.foodmindbackend.auth.domain.RefreshToken;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Service
public class RefreshSession {

    private final AuthSessionRepository authSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    public RefreshSession(
            AuthSessionRepository authSessionRepository,
            UserAccountRepository userAccountRepository,
            AuthTokenService authTokenService,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.authTokenService = authTokenService;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        AuthSession session = authSessionRepository.findByRefreshTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(this::authenticationFailed);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!session.activeAt(now)) {
            if (session.rotatedAt() != null || session.revokedAt() != null) {
                authSessionRepository.revokeFamily(session.tokenFamilyId(), now);
            }
            throw authenticationFailed();
        }

        User user = userAccountRepository.findById(session.userId())
                .filter(candidate -> candidate.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> {
                    authSessionRepository.revokeFamily(session.tokenFamilyId(), now);
                    return authenticationFailed();
                });
        return authTokenService.rotate(user, session);
    }

    private String hash(String rawRefreshToken) {
        try {
            return RefreshToken.fromRaw(rawRefreshToken).hash();
        } catch (IllegalArgumentException exception) {
            throw authenticationFailed();
        }
    }

    private ApiException authenticationFailed() {
        return new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Refresh session is invalid.");
    }
}
