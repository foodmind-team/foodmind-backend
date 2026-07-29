package com.foodmind.foodmindbackend.common.error;

import org.springframework.http.HttpStatus;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request contains invalid fields."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "The request body is malformed or contains unsupported values."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to access this resource."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current resource state."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "The idempotency key conflicts with a previous request."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please retry later."),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A required upstream service is unavailable."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
