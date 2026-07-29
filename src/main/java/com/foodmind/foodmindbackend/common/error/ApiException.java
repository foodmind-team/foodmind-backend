package com.foodmind.foodmindbackend.common.error;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiFieldError> fieldErrors;
    private final HttpStatus status;
    private final String safeMessage;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.status(), errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, errorCode.status(), safeMessage);
    }

    public ApiException(ErrorCode errorCode, HttpStatus status, String safeMessage) {
        this(errorCode, status, safeMessage, List.of());
    }

    public ApiException(ErrorCode errorCode, String safeMessage, List<ApiFieldError> fieldErrors) {
        this(errorCode, errorCode.status(), safeMessage, fieldErrors);
    }

    public ApiException(ErrorCode errorCode, HttpStatus status, String safeMessage, List<ApiFieldError> fieldErrors) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.fieldErrors = List.copyOf(fieldErrors);
        this.status = status;
        this.safeMessage = safeMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public List<ApiFieldError> fieldErrors() {
        return fieldErrors;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
