package com.reconcile.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClusterResolutionTest {

    private final LedgerEntryId e1 = LedgerEntryId.generate();
    private final LedgerEntryId e2 = LedgerEntryId.generate();
    private final LedgerEntryId e3 = LedgerEntryId.generate();
    private final AmbiguousCluster cluster = new AmbiguousCluster(List.of(e1, e2, e3));

    @Test
    void pairingPlusResidualCoveringAllMembersIsValid() {
        ClusterResolution resolution =
                ClusterResolution.of(cluster, UUID.randomUUID(), List.of(new Pairing(e1, e2)), List.of(e3));

        assertThat(resolution.pairings()).hasSize(1);
        assertThat(resolution.leftUnmatched()).containsExactly(e3);
    }

    @Test
    void allMembersInResidualWithNoPairingsIsValid() {
        ClusterResolution resolution = ClusterResolution.of(cluster, UUID.randomUUID(), List.of(), List.of(e1, e2, e3));

        assertThat(resolution.pairings()).isEmpty();
        assertThat(resolution.leftUnmatched()).containsExactlyInAnyOrder(e1, e2, e3);
    }

    @Test
    void omittingAMemberIsInvalid() {
        assertThatThrownBy(
                        () -> ClusterResolution.of(cluster, UUID.randomUUID(), List.of(new Pairing(e1, e2)), List.of()))
                .isInstanceOf(InvalidResolutionException.class);
    }

    @Test
    void referencingAnEntryOutsideTheClusterIsInvalid() {
        LedgerEntryId outsider = LedgerEntryId.generate();
        assertThatThrownBy(() -> ClusterResolution.of(
                        cluster, UUID.randomUUID(), List.of(new Pairing(e1, e2)), List.of(e3, outsider)))
                .isInstanceOf(InvalidResolutionException.class);
    }

    @Test
    void assigningAMemberTwiceIsInvalid() {
        assertThatThrownBy(() ->
                        ClusterResolution.of(cluster, UUID.randomUUID(), List.of(new Pairing(e1, e2)), List.of(e2, e3)))
                .isInstanceOf(InvalidResolutionException.class);
    }

    @Test
    void selfPairingIsInvalid() {
        assertThatThrownBy(() -> new Pairing(e1, e1)).isInstanceOf(InvalidResolutionException.class);
    }

    @Test
    void pairingIsCanonicallyOrderedRegardlessOfConstructionOrder() {
        Pairing ab = new Pairing(e1, e2);
        Pairing ba = new Pairing(e2, e1);
        assertThat(ab).isEqualTo(ba);
    }
}
