-- V7: Discrepancy persistence (normalized, FK-backed) + audit_decision widening
-- =========================================================================

-- 1. Restructure discrepancy table.
--    The table is provably empty (nothing in the codebase writes to it).
--    Nothing FKs into it. Safe to drop and recreate with the normalised shape.
DROP TABLE discrepancy;

CREATE TABLE discrepancy (
    id                 UUID        NOT NULL,
    match_run_id       UUID        NOT NULL,
    type               VARCHAR(20) NOT NULL,
    unmatched_entry_id UUID,                        -- NULL for AMBIGUOUS rows
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_discrepancy    PRIMARY KEY (id),
    CONSTRAINT chk_disc_type     CHECK (type IN ('UNMATCHED','AMBIGUOUS')),
    CONSTRAINT fk_disc_run       FOREIGN KEY (match_run_id)       REFERENCES match_run(id),
    CONSTRAINT fk_disc_entry     FOREIGN KEY (unmatched_entry_id) REFERENCES ledger_entry(id),
    CONSTRAINT chk_disc_variant  CHECK (
        (type = 'UNMATCHED' AND unmatched_entry_id IS NOT NULL) OR
        (type = 'AMBIGUOUS' AND unmatched_entry_id IS NULL)
    )
);

-- 2. One row per cluster member; both FKs enforced at DB level.
CREATE TABLE ambiguous_cluster_member (
    discrepancy_id  UUID NOT NULL,
    ledger_entry_id UUID NOT NULL,
    CONSTRAINT pk_ambiguous_cluster_member
        PRIMARY KEY (discrepancy_id, ledger_entry_id),
    CONSTRAINT fk_acm_discrepancy
        FOREIGN KEY (discrepancy_id)  REFERENCES discrepancy(id),
    CONSTRAINT fk_acm_entry
        FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry(id)
);

-- 3. Widen audit_decision CHECK to admit 'AMBIGUOUS'.
--    Engine already emits this value (DefaultMatchingEngine line 129); V3 CHECK blocked it.
ALTER TABLE audit_decision DROP CONSTRAINT chk_decision;
ALTER TABLE audit_decision ADD CONSTRAINT chk_decision
    CHECK (decision IN ('MATCHED','UNMATCHED','AMBIGUOUS'));
