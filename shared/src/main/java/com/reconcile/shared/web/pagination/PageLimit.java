package com.reconcile.shared.web.pagination;

/** Keyset page-size contract: {@code limit} defaults to 50, capped at 200. Out-of-range → 400. */
public final class PageLimit {

    public static final int DEFAULT = 50;
    public static final int MAX = 200;

    private PageLimit() {}

    /** @param requested raw {@code limit} query param, or {@code null} when absent (→ {@link #DEFAULT}). */
    public static int resolve(Integer requested) {
        if (requested == null) {
            return DEFAULT;
        }
        if (requested < 1 || requested > MAX) {
            throw new InvalidPageLimitException("limit must be between 1 and " + MAX + ", got " + requested);
        }
        return requested;
    }
}
