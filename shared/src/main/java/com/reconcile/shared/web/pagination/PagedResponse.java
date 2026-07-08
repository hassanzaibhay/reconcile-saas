package com.reconcile.shared.web.pagination;

import java.util.List;

/**
 * Uniform keyset-pagination envelope for every list endpoint. Deliberately carries no
 * {@code total}/{@code totalPages}: keyset pagination cannot supply them cheaply, and {@code COUNT(*)}
 * on a growing tenant-scoped financial table under load is a footgun. Never return Spring
 * {@code Page<T>} on the wire — it advertises counts this contract cannot honor.
 *
 * @param nextCursor opaque continuation token ({@link CursorCodec}); {@code null} when {@code hasMore}
 *     is false.
 */
public record PagedResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public PagedResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PagedResponse<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new PagedResponse<>(items, nextCursor, hasMore);
    }
}
