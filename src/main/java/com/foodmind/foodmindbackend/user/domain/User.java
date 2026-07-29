package com.foodmind.foodmindbackend.user.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record User(
        UUID id,
        String email,
        String normalisedEmail,
        String passwordHash,
        String displayName,
        UserRole role,
        UserStatus status,
        String timeZone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastLoginAt,
        OffsetDateTime deactivatedAt,
        long version) {
}
