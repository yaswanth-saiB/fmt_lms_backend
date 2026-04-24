package com.fmt.fmt_backend.exception;

import com.fmt.fmt_backend.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handle validation errors (e.g., 1-letter last name)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error.getObjectName();

            if (error instanceof FieldError) {
                fieldName = ((FieldError) error).getField();
            }

            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("❌ Validation failed: {}", errors);

        ApiResponse<Map<String, String>> response = ApiResponse.error(
                "Validation failed. Please check your input.",
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Handle bad credentials (wrong password)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<String>> handleBadCredentialsException(
            BadCredentialsException ex) {

        log.warn("🔐 Bad credentials: {}", ex.getMessage());

        ApiResponse<String> response = ApiResponse.error(
                "Invalid email or password"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // Handle locked accounts
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<String>> handleLockedException(
            LockedException ex) {

        log.warn("🔒 Account locked: {}", ex.getMessage());

        ApiResponse<String> response = ApiResponse.error(
                "Account is locked. Please try again later."
        );

        return ResponseEntity.status(HttpStatus.LOCKED).body(response);
    }

    // Handle ResponseStatusException (404, 400, 409, 403 etc thrown from services)
    // These are expected business errors — log as WARN, return the correct status + message
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<String>> handleResponseStatusException(
            ResponseStatusException ex) {

        int statusCode = ex.getStatusCode().value();
        // Strip the status prefix Zoom adds: "409 CONFLICT \"message\"" → "message"
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        if (statusCode >= 500) {
            log.error("💥 Server error {}: {}", statusCode, message);
        } else {
            log.warn("⚠️ Request rejected {}: {}", statusCode, message);
        }

        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(message));
    }

    // Handle RuntimeException thrown from service layer (business validation errors)
    // e.g. "Email already registered", "OTP cooldown active"
    // Log as WARN — these are expected, not system failures
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(
            RuntimeException ex) {

        log.warn("⚠️ Business error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Handle truly unexpected errors — log with full stack trace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGlobalException(
            Exception ex, WebRequest request) {

        log.error("💥 Unexpected error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}