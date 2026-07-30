package com.foodmind.foodmindbackend.chat.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatSession(
        UUID id,
        String title,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
