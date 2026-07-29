package com.foodmind.foodmindbackend.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.common.security.JwtIssuer;
import com.foodmind.foodmindbackend.common.security.SecurityProperties;
import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.domain.UserRole;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

class JwtIssuerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void validatesIssuerAudienceSignatureAndExpiry() {
        SecurityProperties properties = new SecurityProperties();
        JwtIssuer issuer = new JwtIssuer(properties, CLOCK);
        String token = issuer.issueAccessToken(user()).token();

        properties.getJwt().setAudience("another-audience");
        assertThatThrownBy(() -> issuer.verify(token)).isInstanceOf(BadCredentialsException.class);

        properties.getJwt().setAudience("foodmind-clients");
        properties.getJwt().setIssuer("another-issuer");
        assertThatThrownBy(() -> issuer.verify(token)).isInstanceOf(BadCredentialsException.class);

        properties.getJwt().setIssuer("foodmind-local");
        properties.getJwt().setSecret("another-local-development-jwt-secret-at-least-32-bytes");
        assertThatThrownBy(() -> issuer.verify(token)).isInstanceOf(BadCredentialsException.class);

        SecurityProperties expiredProperties = new SecurityProperties();
        expiredProperties.getJwt().setAccessTokenTtl(Duration.ofSeconds(-1));
        JwtIssuer expiredIssuer = new JwtIssuer(expiredProperties, CLOCK);
        String expiredToken = expiredIssuer.issueAccessToken(user()).token();
        assertThatThrownBy(() -> expiredIssuer.verify(expiredToken)).isInstanceOf(BadCredentialsException.class);
    }

    private User user() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        return new User(
                UUID.randomUUID(),
                "jwt@example.test",
                "jwt@example.test",
                "$2a$10$placeholder",
                "JWT User",
                UserRole.USER,
                UserStatus.ACTIVE,
                "Asia/Singapore",
                now,
                now,
                null,
                null,
                0);
    }
}
