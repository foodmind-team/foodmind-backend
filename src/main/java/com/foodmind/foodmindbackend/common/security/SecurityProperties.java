package com.foodmind.foodmindbackend.common.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@ConfigurationProperties(prefix = "foodmind.security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private final Refresh refresh = new Refresh();
    private final Web web = new Web();

    public Jwt getJwt() {
        return jwt;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public Web getWeb() {
        return web;
    }

    public static class Jwt {
        private String issuer = "foodmind-local";
        private String audience = "foodmind-clients";
        private String secret = "local-development-jwt-secret-change-me-at-least-32-bytes";
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }
    }

    public static class Refresh {
        private Duration tokenTtl = Duration.ofDays(30);
        private Duration cleanupRetention = Duration.ofDays(7);

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public Duration getCleanupRetention() {
            return cleanupRetention;
        }

        public void setCleanupRetention(Duration cleanupRetention) {
            this.cleanupRetention = cleanupRetention;
        }
    }

    public static class Web {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:8080"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
