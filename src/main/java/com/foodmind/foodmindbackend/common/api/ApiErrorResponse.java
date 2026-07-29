package com.foodmind.foodmindbackend.common.api;

import java.time.Instant;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<ApiFieldError> fieldErrors) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiErrorResponse now(
            int status,
            String code,
            String message,
            String path,
            String traceId,
            List<ApiFieldError> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, traceId, fieldErrors);
    }
}
