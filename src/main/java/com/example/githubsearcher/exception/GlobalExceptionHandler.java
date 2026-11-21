package com.example.githubsearcher.exception;

import com.example.githubsearcher.dto.response.ApiErrorResponse;
import com.example.githubsearcher.dto.response.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getAllValidationResults().forEach(result -> {
            String field = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    fieldErrors.add(new FieldErrorDetail(field == null ? "request" : field, error.getDefaultMessage())));
        });
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed JSON request", request, null);
    }

    @ExceptionHandler(GitHubRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(GitHubRateLimitException ex, HttpServletRequest request) {
        log.warn("GitHub rate limit on {}", request.getRequestURI());
        ApiErrorResponse body = errorBody(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, null);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (ex.getRetryAfter() != null && !ex.getRetryAfter().isBlank()) {
            builder.header(HttpHeaders.RETRY_AFTER, ex.getRetryAfter());
        }
        return builder.body(body);
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubApi(GitHubApiException ex, HttpServletRequest request) {
        log.warn("GitHub API error status {} on {}: {}", ex.getStatus(), request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null || status.is2xxSuccessful() || status.is3xxRedirection()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return respond(status, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private static ResponseEntity<ApiErrorResponse> respond(
            HttpStatus status, String message, HttpServletRequest request, List<FieldErrorDetail> fieldErrors) {
        return ResponseEntity.status(status).body(errorBody(status, message, request, fieldErrors));
    }

    private static ApiErrorResponse errorBody(
            HttpStatus status, String message, HttpServletRequest request, List<FieldErrorDetail> fieldErrors) {
        List<FieldErrorDetail> errors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
        return new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                errors);
    }
}
