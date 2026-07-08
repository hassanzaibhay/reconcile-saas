package com.reconcile.shared.error;

public enum ErrorCode {
    MISSING_TENANT,
    UNAUTHENTICATED,
    FORBIDDEN,
    VALIDATION_ERROR,
    NOT_FOUND,
    CONFLICT,
    IDEMPOTENCY_CONFLICT,
    CLIENT_ERROR,
    INTERNAL_ERROR,
}
