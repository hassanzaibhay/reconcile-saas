package com.reconcile.reconciliation.domain;

import java.util.UUID;

public record MatchRunId(UUID value) {

    public static MatchRunId generate() {
        return new MatchRunId(UUID.randomUUID());
    }

    public static MatchRunId of(UUID value) {
        return new MatchRunId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
