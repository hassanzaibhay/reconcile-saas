package com.reconcile.ingestion.domain;

import java.time.Instant;
import java.util.UUID;

public record IngestionRun(
        UUID id,
        String feedId,
        IngestionStatus status,
        String contentHash,
        String idempotencyKey,
        Integer rowCount,
        Instant startedAt,
        Instant completedAt,
        String errorMessage) {

    public static IngestionRun start(String feedId, String contentHash, String idempotencyKey) {
        return new IngestionRun(
                UUID.randomUUID(),
                feedId,
                IngestionStatus.PENDING,
                contentHash,
                idempotencyKey,
                null,
                Instant.now(),
                null,
                null);
    }

    public IngestionRun complete(int rowCount) {
        return new IngestionRun(
                id,
                feedId,
                IngestionStatus.COMPLETED,
                contentHash,
                idempotencyKey,
                rowCount,
                startedAt,
                Instant.now(),
                null);
    }

    public IngestionRun fail(String error) {
        return new IngestionRun(
                id,
                feedId,
                IngestionStatus.FAILED,
                contentHash,
                idempotencyKey,
                rowCount,
                startedAt,
                Instant.now(),
                error);
    }
}
