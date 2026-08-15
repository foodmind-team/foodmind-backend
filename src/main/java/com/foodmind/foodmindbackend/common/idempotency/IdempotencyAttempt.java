package com.foodmind.foodmindbackend.common.idempotency;

/**
 * Describes whether the current caller owns an idempotent operation or is observing an existing one.
 */
public record IdempotencyAttempt(
        IdempotencyRecord record,
        boolean acquired) {
}
