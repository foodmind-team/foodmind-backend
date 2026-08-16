package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OneMapTokenProvider {
    private final Object refreshLock = new Object();
    private final OneMapRoutingProperties properties;
    private final OneMapCredentialsSource credentialsSource;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient authenticationClient;
    private volatile CachedToken cachedToken;

    public OneMapTokenProvider(
            OneMapRoutingProperties properties,
            OneMapCredentialsSource credentialsSource,
            ObjectMapper objectMapper,
            Clock clock) {
        this.properties = properties;
        this.credentialsSource = credentialsSource;
        this.objectMapper = objectMapper;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.authenticationClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public String token() {
        if (!properties.isEnabled()) {
            throw unavailable();
        }
        if (properties.getCredentialsSecretArn().isBlank()) {
            if (properties.getApiToken().isBlank()) {
                throw unavailable();
            }
            return properties.getApiToken();
        }

        Instant now = clock.instant();
        CachedToken current = cachedToken;
        if (isReusable(current, now)) {
            return current.value();
        }
        synchronized (refreshLock) {
            now = clock.instant();
            current = cachedToken;
            if (isReusable(current, now)) {
                return current.value();
            }
            cachedToken = authenticate(now);
            return cachedToken.value();
        }
    }

    public boolean invalidate(String usedToken) {
        if (properties.getCredentialsSecretArn().isBlank()) {
            return false;
        }
        synchronized (refreshLock) {
            if (cachedToken != null && cachedToken.value().equals(usedToken)) {
                cachedToken = null;
            }
        }
        return true;
    }

    private boolean isReusable(CachedToken token, Instant now) {
        return token != null && token.expiresAt().minus(properties.getRefreshSkew()).isAfter(now);
    }

    private CachedToken authenticate(Instant now) {
        try {
            OneMapCredentials credentials = credentialsSource.load();
            String raw = authenticationClient.post()
                    .uri("/api/auth/post/getToken")
                    .body(Map.of("email", credentials.email(), "password", credentials.password()))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String value = root.path("access_token").asText("");
            long expiryTimestamp = root.path("expiry_timestamp").asLong(0);
            Instant expiresAt = Instant.ofEpochSecond(expiryTimestamp);
            if (value.isBlank() || !expiresAt.isAfter(now.plus(properties.getRefreshSkew()))) {
                throw unavailable();
            }
            return new CachedToken(value, expiresAt);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                "Walking directions are temporarily unavailable.");
    }

    private record CachedToken(String value, Instant expiresAt) { }
}
