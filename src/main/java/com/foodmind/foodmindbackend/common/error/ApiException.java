package com.foodmind.foodmindbackend.common.error;

import org.springframework.http.HttpStatus;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final String safeMessage;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.status(), errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, errorCode.status(), safeMessage);
    }

    public ApiException(ErrorCode errorCode, HttpStatus status, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.status = status;
        this.safeMessage = safeMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
