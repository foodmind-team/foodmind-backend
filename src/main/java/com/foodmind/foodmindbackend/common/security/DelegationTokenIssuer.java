package com.foodmind.foodmindbackend.common.security;

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
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Component
public class DelegationTokenIssuer {

    public static final String SCOPE_CHAT_SEARCH = "CHAT_SEARCH";
    public static final String SCOPE_CHAT_REFERENCE_RESOLVE = "CHAT_REFERENCE_RESOLVE";

    private final SecurityProperties properties;
    private final Clock clock;

    public DelegationTokenIssuer(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedDelegationToken issue(UUID userId, String traceId, List<String> scopes, List<UUID> referenceIds) {
        OffsetDateTime issuedAt = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = issuedAt.plus(properties.getDelegation().getTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(properties.getJwt().getIssuer())
                .audience(properties.getDelegation().getAudience())
                .issueTime(Date.from(issuedAt.toInstant()))
                .notBeforeTime(Date.from(issuedAt.toInstant()))
                .expirationTime(Date.from(expiresAt.toInstant()))
                .jwtID(UUID.randomUUID().toString())
                .claim("typ", "foodmind-agent-delegation")
                .claim("trace", traceId)
                .claim("scopes", scopes)
                .claim("referenceIds", referenceIds.stream().map(UUID::toString).toList())
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJwt.sign(new MACSigner(secretBytes()));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign delegation token.", exception);
        }
        return new IssuedDelegationToken(signedJwt.serialize(), expiresAt);
    }

    public VerifiedDelegationToken verify(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secretBytes()))) {
                throw new BadCredentialsException("Invalid delegation token.");
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!properties.getJwt().getIssuer().equals(claims.getIssuer())) {
                throw new BadCredentialsException("Invalid delegation issuer.");
            }
            if (!claims.getAudience().contains(properties.getDelegation().getAudience())) {
                throw new BadCredentialsException("Invalid delegation audience.");
            }
            if (!"foodmind-agent-delegation".equals(claims.getStringClaim("typ"))) {
                throw new BadCredentialsException("Invalid delegation type.");
            }
            if (claims.getNotBeforeTime() == null || claims.getExpirationTime() == null) {
                throw new BadCredentialsException("Delegation validity window is missing.");
            }
            if (claims.getNotBeforeTime().toInstant().isAfter(now.toInstant())
                    || !claims.getExpirationTime().toInstant().isAfter(now.toInstant())) {
                throw new BadCredentialsException("Delegation token is not currently valid.");
            }
            List<String> scopes = claims.getStringListClaim("scopes");
            List<String> referenceIdClaims = claims.getStringListClaim("referenceIds");
            List<UUID> referenceIds = (referenceIdClaims == null ? List.<String>of() : referenceIdClaims).stream()
                    .map(UUID::fromString)
                    .toList();
            return new VerifiedDelegationToken(
                    UUID.fromString(claims.getSubject()),
                    claims.getStringClaim("trace"),
                    scopes == null ? List.of() : scopes,
                    referenceIds);
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid delegation token.", exception);
        }
    }

    private byte[] secretBytes() {
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes.");
        }
        return secret;
    }

    public record IssuedDelegationToken(String token, OffsetDateTime expiresAt) {
    }

    public record VerifiedDelegationToken(UUID userId, String traceId, List<String> scopes, List<UUID> referenceIds) {
    }
}
