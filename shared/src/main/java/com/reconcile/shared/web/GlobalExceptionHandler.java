package com.reconcile.shared.web;

import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.error.ApiError;
import com.reconcile.shared.error.ErrorCode;
import com.reconcile.shared.web.pagination.InvalidCursorException;
import com.reconcile.shared.web.pagination.InvalidPageLimitException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingTenantException.class)
    public ResponseEntity<ApiError> onMissingTenant(MissingTenantException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, ErrorCode.MISSING_TENANT, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.unprocessableEntity()
                .body(ApiError.of(422, ErrorCode.VALIDATION_ERROR, "Validation failed", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraint(ConstraintViolationException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of(422, ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler({InvalidCursorException.class, InvalidPageLimitException.class})
    public ResponseEntity<ApiError> onBadPagination(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }

    /**
     * Folds {@link ResponseStatusException} into the {@code ApiError} envelope so controllers that throw
     * it (resolve 404, ingestion 400) no longer leak Spring's default {@code {timestamp,status,error,path}}
     * body. Status is passed through verbatim; only the {@code code} is derived — a 4xx never becomes
     * {@code INTERNAL_ERROR}.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> onResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        return ResponseEntity.status(status).body(ApiError.of(status.value(), codeForStatus(status), ex.getReason()));
    }

    private static ErrorCode codeForStatus(HttpStatusCode status) {
        int value = status.value();
        return switch (value) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 409 -> ErrorCode.CONFLICT;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.CLIENT_ERROR;
        };
    }
}
