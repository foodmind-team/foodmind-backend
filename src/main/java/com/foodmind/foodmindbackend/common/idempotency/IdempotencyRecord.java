package com.foodmind.foodmindbackend.common.idempotency;

import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record IdempotencyRecord(
        UUID id,
        String state,
        String requestHash,
        UUID resourceId) {
}
