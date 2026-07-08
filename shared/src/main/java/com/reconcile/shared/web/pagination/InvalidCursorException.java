package com.reconcile.shared.web.pagination;

/** A continuation cursor was malformed, tampered with, or minted under a different filter set. Maps to 400. */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String message) {
        super(message);
    }
}
