package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.Objects;

/**
 * An operator-created pairing between two ledger entries during cluster resolution. Canonically
 * ordered by UUID (matches the engine's own left/right convention — the columns carry no source
 * side, so ordering is for self-pair rejection and deterministic dedup only).
 */
public record Pairing(LedgerEntryId a, LedgerEntryId b) {

    public Pairing {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        if (a.equals(b)) {
            throw new InvalidResolutionException("pairing references the same entry twice");
        }
        if (a.value().compareTo(b.value()) > 0) {
            LedgerEntryId t = a;
            a = b;
            b = t;
        }
    }
}
