package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An operator's partition of an {@link AmbiguousCluster} into disjoint pairings plus an explicit
 * unmatched residual. There is no privileged "source" member — every cluster member must appear
 * exactly once across {@code pairings} and {@code leftUnmatched}.
 */
public record ClusterResolution(UUID discrepancyId, List<Pairing> pairings, List<LedgerEntryId> leftUnmatched) {

    public static ClusterResolution of(
            AmbiguousCluster cluster, UUID discrepancyId, List<Pairing> pairings, List<LedgerEntryId> leftUnmatched) {
        validatePartition(cluster, pairings, leftUnmatched);
        return new ClusterResolution(discrepancyId, List.copyOf(pairings), List.copyOf(leftUnmatched));
    }

    private static void validatePartition(
            AmbiguousCluster cluster, List<Pairing> pairings, List<LedgerEntryId> leftUnmatched) {
        Map<LedgerEntryId, Long> counts = Stream.concat(
                        pairings.stream().flatMap(p -> Stream.of(p.a(), p.b())), leftUnmatched.stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        if (!counts.keySet().equals(Set.copyOf(cluster.members()))) {
            throw new InvalidResolutionException("resolution references entries outside cluster or omits members");
        }
        if (counts.values().stream().anyMatch(c -> c != 1L)) {
            throw new InvalidResolutionException("entry assigned to more than one slot");
        }
    }
}
