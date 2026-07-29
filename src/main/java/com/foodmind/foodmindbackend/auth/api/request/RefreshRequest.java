package com.foodmind.foodmindbackend.auth.api.request;

import com.foodmind.foodmindbackend.auth.domain.ClientType;
import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record RefreshRequest(
        @Size(max = 4096) String refreshToken,
        ClientType clientType) {
}
