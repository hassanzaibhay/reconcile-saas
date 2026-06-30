package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.Comparator;
import java.util.List;

/**
 * A set of mutually-confusable ledger entries that the engine cannot automatically resolve.
 * Produced when a connected component in the match graph has 3+ members. Has no distinguished
 * "source" — all members are equally candidates. An operator must choose the correct pairing.
 */
public record AmbiguousCluster(List<LedgerEntryId> members) {

    public AmbiguousCluster {
        members = List.copyOf(members.stream()
                .sorted(Comparator.comparing(LedgerEntryId::value))
                .toList());
    }
}
