package com.foodmind.foodmindbackend.auth.api.request;

import com.foodmind.foodmindbackend.auth.domain.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^\\s*[^@\\s]+@[^@\\s]+\\.[^@\\s]+\\s*$") @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 64) String timeZone,
        @NotNull ClientType clientType,
        @Size(max = 100) String deviceLabel) {
}
