package com.foodmind.foodmindbackend.user.api.request;

import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

public record UpdateCurrentUserRequest(
        @Size(max = 100) String displayName,
        @Size(max = 64) String timeZone) {
}
