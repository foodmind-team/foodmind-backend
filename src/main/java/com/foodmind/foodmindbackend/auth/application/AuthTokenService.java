package com.foodmind.foodmindbackend.auth.application;

import com.foodmind.foodmindbackend.auth.application.port.AuthSessionRepository;
import com.foodmind.foodmindbackend.auth.domain.AuthSession;
import com.foodmind.foodmindbackend.auth.domain.ClientType;
import com.foodmind.foodmindbackend.auth.domain.RefreshToken;
import com.foodmind.foodmindbackend.common.security.JwtIssuer;
import com.foodmind.foodmindbackend.common.security.SecurityProperties;
import com.foodmind.foodmindbackend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Service
class AuthTokenService {

    private final AuthSessionRepository authSessionRepository;
    private final JwtIssuer jwtIssuer;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    AuthTokenService(
            AuthSessionRepository authSessionRepository,
            JwtIssuer jwtIssuer,
            SecurityProperties securityProperties,
            Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.jwtIssuer = jwtIssuer;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    AuthTokens issueNewSession(User user, ClientType clientType, String deviceLabel) {
        OffsetDateTime issuedAt = OffsetDateTime.now(clock);
        return issue(user, UUID.randomUUID(), issuedAt, clientType, deviceLabel, null);
    }

    AuthTokens rotate(User user, AuthSession predecessor) {
        OffsetDateTime rotatedAt = OffsetDateTime.now(clock);
        OffsetDateTime successorIssuedAt = rotatedAt.plus(Duration.ofMillis(1));
        AuthTokens tokens = issue(
                user,
                predecessor.tokenFamilyId(),
                successorIssuedAt,
                predecessor.clientType(),
                predecessor.deviceLabel(),
                predecessor);
        return new AuthTokens(
                user.id(),
                tokens.accessToken(),
                tokens.expiresIn(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt(),
                tokens.sessionId(),
                tokens.tokenFamilyId());
    }

    private AuthTokens issue(
            User user,
            UUID tokenFamilyId,
            OffsetDateTime issuedAt,
            ClientType clientType,
            String deviceLabel,
            AuthSession predecessor) {
        RefreshToken refreshToken = RefreshToken.generate();
        OffsetDateTime refreshExpiresAt = issuedAt.plus(securityProperties.getRefresh().getTokenTtl());
        AuthSession successor = new AuthSession(
                UUID.randomUUID(),
                user.id(),
                tokenFamilyId,
                refreshToken.hash(),
                issuedAt,
                refreshExpiresAt,
                null,
                null,
                null,
                clientType,
                cleanDeviceLabel(deviceLabel));

        if (predecessor == null) {
            authSessionRepository.save(successor);
        } else {
            authSessionRepository.rotate(predecessor, successor, issuedAt.minus(Duration.ofMillis(1)));
        }

        JwtIssuer.IssuedAccessToken accessToken = jwtIssuer.issueAccessToken(user);
        return new AuthTokens(
                user.id(),
                accessToken.token(),
                accessToken.expiresIn(),
                accessToken.expiresAt(),
                refreshToken.raw(),
                refreshExpiresAt,
                successor.id(),
                tokenFamilyId);
    }

    private String cleanDeviceLabel(String deviceLabel) {
        if (deviceLabel == null || deviceLabel.isBlank()) {
            return null;
        }
        String cleaned = deviceLabel.trim();
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, 100);
    }
}
