package com.foodmind.foodmindbackend.common.security;

import com.foodmind.foodmindbackend.user.domain.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Component
public class JwtIssuer {

    private final SecurityProperties properties;
    private final Clock clock;

    public JwtIssuer(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issueAccessToken(User user) {
        OffsetDateTime issuedAt = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = issuedAt.plus(properties.getJwt().getAccessTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.id().toString())
                .issuer(properties.getJwt().getIssuer())
                .audience(properties.getJwt().getAudience())
                .issueTime(Date.from(issuedAt.toInstant()))
                .notBeforeTime(Date.from(issuedAt.toInstant()))
                .expirationTime(Date.from(expiresAt.toInstant()))
                .jwtID(UUID.randomUUID().toString())
                .claim("role", user.role().name())
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJwt.sign(new MACSigner(secretBytes()));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token.", exception);
        }
        return new IssuedAccessToken(signedJwt.serialize(), expiresAt, properties.getJwt().getAccessTokenTtl().toSeconds());
    }

    public VerifiedAccessToken verify(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secretBytes()))) {
                throw new BadCredentialsException("Invalid access token.");
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            OffsetDateTime now = OffsetDateTime.now(clock);
            requireEquals(properties.getJwt().getIssuer(), claims.getIssuer());
            if (!claims.getAudience().contains(properties.getJwt().getAudience())) {
                throw new BadCredentialsException("Invalid access token audience.");
            }
            requireTime(claims.getIssueTime(), "iat");
            requireTime(claims.getNotBeforeTime(), "nbf");
            requireTime(claims.getExpirationTime(), "exp");
            if (claims.getNotBeforeTime().toInstant().isAfter(now.toInstant())) {
                throw new BadCredentialsException("Access token is not valid yet.");
            }
            if (!claims.getExpirationTime().toInstant().isAfter(now.toInstant())) {
                throw new BadCredentialsException("Access token has expired.");
            }
            if (claims.getJWTID() == null || claims.getJWTID().isBlank()) {
                throw new BadCredentialsException("Missing access token id.");
            }
            String role = claims.getStringClaim("role");
            if (role == null || role.isBlank()) {
                throw new BadCredentialsException("Missing access token role.");
            }
            return new VerifiedAccessToken(UUID.fromString(claims.getSubject()), role);
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid access token.", exception);
        }
    }

    private void requireEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new BadCredentialsException("Invalid access token issuer.");
        }
    }

    private void requireTime(Date date, String claim) {
        if (date == null) {
            throw new BadCredentialsException("Missing access token " + claim + ".");
        }
    }

    private byte[] secretBytes() {
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes.");
        }
        return secret;
    }

    public record IssuedAccessToken(String token, OffsetDateTime expiresAt, long expiresIn) {
    }

    public record VerifiedAccessToken(UUID userId, String role) {
    }
}
