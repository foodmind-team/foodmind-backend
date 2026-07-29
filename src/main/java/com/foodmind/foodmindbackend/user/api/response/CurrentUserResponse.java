package com.foodmind.foodmindbackend.user.api.response;

import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.domain.UserRole;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record CurrentUserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        String timeZone,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.status(),
                user.timeZone(),
                user.version(),
                user.createdAt(),
                user.updatedAt());
    }
}
