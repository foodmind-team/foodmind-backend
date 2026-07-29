package com.foodmind.foodmindbackend.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record RefreshToken(String raw, String hash) {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    public static RefreshToken generate() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        String raw = HEX.formatHex(bytes) + "." + UUID.randomUUID();
        return fromRaw(raw);
    }

    public static RefreshToken fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new RefreshToken(raw, HEX.formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
