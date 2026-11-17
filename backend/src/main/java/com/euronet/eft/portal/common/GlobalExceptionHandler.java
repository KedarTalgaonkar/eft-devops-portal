package com.euronet.eft.portal.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    /**
     * AI requests that cannot be served (off, limit reached, provider error): the message is written for users.
     */
    @ExceptionHandler(com.euronet.eft.portal.ai.AiException.class)
    ResponseEntity<ApiError> ai(com.euronet.eft.portal.ai.AiException ex, HttpServletRequest req) {
        return build(ex.status(), ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    /**
     * Wrong type or enum value in a path/query parameter; never echo Java type names back to the client.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String allowed = ex.getRequiredType() != null && ex.getRequiredType().isEnum() ? " (allowed: " + String.join(", ", java.util.Arrays.stream(ex.getRequiredType().getEnumConstants()).map(String::valueOf).toList()) + ")" : "";
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'" + allowed, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or missing request body", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var details = ex.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        var body = new ApiError(Instant.now(), 400, "Bad Request", "Validation failed", req.getRequestURI(), details);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "File is larger than the 10 MB limit", req);
    }

    @ExceptionHandler(IntegrationUnavailableException.class)
    ResponseEntity<ApiError> upstream(IntegrationUnavailableException ex, HttpServletRequest req) {
        log.warn("Integration call failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req);
    }

    /**
     * Anything unexpected: log it with an id, return only the id (no exception text or stack trace).
     * Spring's own web exceptions (bad JSON, wrong method, ...) keep their proper 4xx status.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            status = status == null ? HttpStatus.BAD_REQUEST : status;
            return build(status, status.getReasonPhrase(), req);
        }
        String id = UUID.randomUUID().toString();
        log.error("Unhandled error {} on {} {}", id, req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error (reference " + id + ")", req);
    }
}
