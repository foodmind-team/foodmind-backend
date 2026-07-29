package com.foodmind.foodmindbackend.group.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record JoinGroupRequest(@NotBlank @Size(max = 256) String token) {
}
