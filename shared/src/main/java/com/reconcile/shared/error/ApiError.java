package com.reconcile.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int status, ErrorCode code, String message, List<FieldError> fieldErrors, Instant timestamp) {

    public record FieldError(String field, String message) {}

    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(status, code, message, List.of(), Instant.now());
    }
}
