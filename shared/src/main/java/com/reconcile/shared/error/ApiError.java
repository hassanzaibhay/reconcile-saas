package com.reconcile.shared.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * The single error envelope for the whole HTTP surface. {@code traceId} gives support a correlation
 * handle; it is taken from the {@code traceId} MDC key set by the correlation filter, falling back to a
 * fresh UUID when resolved off the request thread.
 */
public record ApiError(
        int status, ErrorCode code, String message, List<FieldError> fieldErrors, Instant timestamp, String traceId) {

    public record FieldError(String field, String message) {}

    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(status, code, message, List.of(), Instant.now(), resolveTraceId());
    }

    public static ApiError of(int status, ErrorCode code, String message, List<FieldError> fieldErrors) {
        return new ApiError(status, code, message, fieldErrors, Instant.now(), resolveTraceId());
    }

    static String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank())
                ? traceId
                : UUID.randomUUID().toString();
    }
}
