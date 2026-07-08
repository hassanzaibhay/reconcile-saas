package com.reconcile.shared.web.pagination;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Opaque keyset-cursor codec. A cursor encodes the sort-key tuple {@code (sortKey, id)} plus a hash of
 * the filter set it was minted under, so a continuation request whose filters changed is rejected
 * (400) rather than silently returning rows from a different query.
 *
 * <p><b>Microsecond precision (critical).</b> The discrepancy/run sort column is Postgres
 * {@code TIMESTAMPTZ} (microsecond resolution); Java {@link Instant} is nanosecond. The sort instant is
 * truncated to microseconds on encode so it round-trips identically to what the database stored. The
 * keyset query that consumes this cursor MUST use the row-tuple comparison so its boundary agrees with
 * the cursor exactly:
 *
 * <pre>{@code
 *   WHERE (created_at, id) < (:cursorCreatedAt, :cursorId)
 *   ORDER BY created_at DESC, id DESC
 *   LIMIT :limit
 * }</pre>
 *
 * <p><b>Filter hash.</b> Full SHA-256 (64 hex chars, untruncated) of the normalized active filter set.
 * Not truncated: cursors are not length-constrained here, and a short hash risks accepting a cursor from
 * the wrong filter context. (This is a page-consistency guard, not a security boundary — tenant scoping
 * is enforced independently at the DB layer.)
 */
public final class CursorCodec {

    private static final String VERSION = "1";
    private static final String SEP = "|";

    private CursorCodec() {}

    public static String encode(Instant sortKey, UUID id, String filterHash) {
        Objects.requireNonNull(sortKey, "sortKey");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(filterHash, "filterHash");
        String payload =
                String.join(SEP, VERSION, sortKey.truncatedTo(ChronoUnit.MICROS).toString(), id.toString(), filterHash);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param expectedFilterHash the hash of the CURRENT request's filter set; a cursor minted under a
     *     different filter set is rejected.
     * @throws InvalidCursorException if the cursor is malformed, has the wrong version, or was minted
     *     under a different filter set.
     */
    public static Cursor decode(String encoded, String expectedFilterHash) {
        Objects.requireNonNull(expectedFilterHash, "expectedFilterHash");
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidCursorException("cursor is empty");
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException("cursor is not valid base64url");
        }
        String[] parts = payload.split("\\" + SEP, -1);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            throw new InvalidCursorException("cursor has an unrecognized structure");
        }
        Instant sortKey;
        UUID id;
        try {
            sortKey = Instant.parse(parts[1]).truncatedTo(ChronoUnit.MICROS);
            id = UUID.fromString(parts[2]);
        } catch (RuntimeException e) {
            throw new InvalidCursorException("cursor payload is malformed");
        }
        String filterHash = parts[3];
        if (!expectedFilterHash.equals(filterHash)) {
            throw new InvalidCursorException("cursor filter set does not match the current request");
        }
        return new Cursor(sortKey, id, filterHash);
    }

    /**
     * Deterministic hash of the active filter set. Entries with a {@code null} value are ignored; the
     * remainder are sorted by key and joined {@code key=value} with {@code &} before hashing, so filter
     * order never changes the hash.
     */
    public static String filterHash(Map<String, String> filters) {
        Objects.requireNonNull(filters, "filters");
        StringBuilder normalized = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(filters).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                normalized.append('&');
            }
            normalized.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sha256Hex(normalized.toString());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
