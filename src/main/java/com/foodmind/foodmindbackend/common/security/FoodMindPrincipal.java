package com.foodmind.foodmindbackend.common.security;

import com.foodmind.foodmindbackend.user.domain.UserRole;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record FoodMindPrincipal(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status) {
}
