package com.reconcile.shared.web.pagination;

/** A {@code limit} query param was out of the accepted range. Maps to 400. */
public class InvalidPageLimitException extends RuntimeException {

    public InvalidPageLimitException(String message) {
        super(message);
    }
}
