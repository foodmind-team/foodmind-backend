package com.foodmind.foodmindbackend.common.error;

import com.foodmind.foodmindbackend.common.api.ApiErrorResponse;
import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Comparator<ApiFieldError> FIELD_ERROR_COMPARATOR =
            Comparator.comparing(ApiFieldError::field)
                    .thenComparing(ApiFieldError::code)
                    .thenComparing(ApiFieldError::message);

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.errorCode(), exception.safeMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .sorted(FIELD_ERROR_COMPARATOR)
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> toFieldErrors(result).stream())
                .sorted(FIELD_ERROR_COMPARATOR)
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        publicFieldName(violation.getPropertyPath().toString()),
                        normaliseCode(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()),
                        violation.getMessage()))
                .sorted(FIELD_ERROR_COMPARATOR)
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        UnrecognizedPropertyException unknownProperty = findCause(exception, UnrecognizedPropertyException.class);
        if (unknownProperty != null) {
            return response(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    request,
                    List.of(new ApiFieldError(unknownProperty.getPropertyName(), "UNKNOWN_FIELD", "Field is not supported.")));
        }
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_JSON,
                ErrorCode.MALFORMED_JSON.defaultMessage(),
                request,
                List.of());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleRequestBinding(Exception exception, HttpServletRequest request) {
        String field = "request";
        if (exception instanceof MissingServletRequestParameterException missing) {
            field = missing.getParameterName();
        } else if (exception instanceof MissingRequestHeaderException missing) {
            field = missing.getHeaderName();
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            field = mismatch.getName();
        }
        String message = exception instanceof MethodArgumentTypeMismatchException
                ? "Value has an invalid type."
                : "Required value is missing.";
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                List.of(new ApiFieldError(field, requestBindingCode(exception), message)));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class, EntityNotFoundException.class})
    ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT,
                ErrorCode.CONFLICT.defaultMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED,
                ErrorCode.ACCESS_DENIED.defaultMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTHENTICATION_REQUIRED,
                ErrorCode.AUTHENTICATION_REQUIRED.defaultMessage(),
                request,
                List.of());
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiErrorResponse> handleUnsupportedRequest(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                List.of(new ApiFieldError("request", "UNSUPPORTED_REQUEST", "Request method or media type is not supported.")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                request,
                List.of());
    }

    public void handleAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        writeResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED, request);
    }

    public void handleAccessDeniedFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writeResponse(response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> fieldErrors) {
        String traceId = traceId(request);
        return ResponseEntity.status(status)
                .header(CorrelationIdFilter.HEADER_NAME, traceId)
                .body(ApiErrorResponse.now(status.value(), code.name(), message, request.getRequestURI(), traceId, fieldErrors));
    }

    private void writeResponse(
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode code,
            HttpServletRequest request) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String traceId = traceId(request);
        ApiErrorResponse body = ApiErrorResponse.now(
                status.value(),
                code.name(),
                code.defaultMessage(),
                request.getRequestURI(),
                traceId,
                List.of());
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER_NAME, traceId);
        response.getWriter().write(toJson(body));
    }

    private String traceId(HttpServletRequest request) {
        String current = CorrelationIdFilter.currentCorrelationId();
        if (current != null) {
            return current;
        }
        return CorrelationIdFilter.acceptOrGenerate(request.getHeader(CorrelationIdFilter.HEADER_NAME));
    }

    private ApiFieldError toFieldError(FieldError fieldError) {
        String code = fieldError.getCode() == null ? "INVALID" : normaliseCode(fieldError.getCode());
        String message = fieldError.getDefaultMessage() == null ? "Value is invalid." : fieldError.getDefaultMessage();
        return new ApiFieldError(fieldError.getField(), code, message);
    }

    private List<ApiFieldError> toFieldErrors(ParameterValidationResult result) {
        String field = result.getMethodParameter().getParameterName();
        if (field == null || field.isBlank()) {
            field = "request";
        }
        String publicField = publicFieldName(field);
        return result.getResolvableErrors().stream()
                .map(error -> new ApiFieldError(publicField, resolvableCode(error), resolvableMessage(error)))
                .toList();
    }

    private String resolvableCode(MessageSourceResolvable resolvable) {
        String[] codes = resolvable.getCodes();
        return codes == null || codes.length == 0 ? "INVALID" : normaliseCode(codes[0]);
    }

    private String resolvableMessage(MessageSourceResolvable resolvable) {
        String message = resolvable.getDefaultMessage();
        return message == null || message.isBlank() ? "Value is invalid." : message;
    }

    private String requestBindingCode(Exception exception) {
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return "TYPE_MISMATCH";
        }
        return "REQUIRED";
    }

    private String publicFieldName(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < path.length()) {
            return path.substring(lastDot + 1);
        }
        return path;
    }

    private String normaliseCode(String code) {
        return code.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace('.', '_')
                .replace('-', '_')
                .toUpperCase();
    }

    private String toJson(ApiErrorResponse body) throws JacksonException {
        return objectMapper.writeValueAsString(body);
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return causeType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
