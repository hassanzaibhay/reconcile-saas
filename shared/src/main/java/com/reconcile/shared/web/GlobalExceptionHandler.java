package com.reconcile.shared.web;

import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.error.ApiError;
import com.reconcile.shared.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingTenantException.class)
    public ResponseEntity<ApiError> onMissingTenant(MissingTenantException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, ErrorCode.MISSING_TENANT, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                        .toList();
        ApiError body =
                new ApiError(
                        422,
                        ErrorCode.VALIDATION_ERROR,
                        "Validation failed",
                        errors,
                        java.time.Instant.now());
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraint(ConstraintViolationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiError.of(422, ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }
}
