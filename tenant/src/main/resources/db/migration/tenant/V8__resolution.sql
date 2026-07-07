-- V8: discrepancy resolution (cluster partition, unmatched review) + entry availability.
-- ==========================================================================================

-- 1. Availability: DB-fail-closed. An entry consumed by any match (engine or manual) gets a
--    matched_entry row. Re-consumption is a 23505, never an app-level pre-check.
CREATE TABLE matched_entry (
    ledger_entry_id UUID        NOT NULL,
    match_result_id UUID        NOT NULL,
    matched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_matched_entry PRIMARY KEY (ledger_entry_id),
    CONSTRAINT fk_me_result FOREIGN KEY (match_result_id) REFERENCES match_result(id),
    CONSTRAINT fk_me_entry  FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry(id)
);

-- 2. Backfill existing engine matches (both sides of each pair). No-op on a fresh schema
--    (nothing seeds match_result). A 23505 here is a pre-existing double-match = real
--    corruption; do not add ON CONFLICT to force it through.
INSERT INTO matched_entry (ledger_entry_id, match_result_id)
SELECT left_entry_id,  id FROM match_result
UNION ALL
SELECT right_entry_id, id FROM match_result;

-- 3. Concurrency + lifecycle guard on discrepancy.
ALTER TABLE discrepancy ADD COLUMN status  VARCHAR(20) NOT NULL DEFAULT 'OPEN'
    CHECK (status IN ('OPEN','RESOLVED'));
ALTER TABLE discrepancy ADD COLUMN version INT NOT NULL DEFAULT 0;

-- 4. One resolution per discrepancy.
CREATE TABLE resolution (
    id             UUID         NOT NULL,
    discrepancy_id UUID         NOT NULL,
    kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('UNMATCHED_REVIEWED','CLUSTER')),
    resolved_by    VARCHAR(255) NOT NULL,
    resolved_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_resolution PRIMARY KEY (id),
    CONSTRAINT fk_res_disc FOREIGN KEY (discrepancy_id) REFERENCES discrepancy(id),
    CONSTRAINT uq_res_disc UNIQUE (discrepancy_id)
);

-- 5. Operator-created pairings for a CLUSTER resolution.
CREATE TABLE resolution_pairing (
    resolution_id  UUID NOT NULL,
    left_entry_id  UUID NOT NULL,
    right_entry_id UUID NOT NULL,
    CONSTRAINT pk_resolution_pairing PRIMARY KEY (resolution_id, left_entry_id, right_entry_id),
    CONSTRAINT fk_rp_res   FOREIGN KEY (resolution_id)  REFERENCES resolution(id),
    CONSTRAINT fk_rp_left  FOREIGN KEY (left_entry_id)  REFERENCES ledger_entry(id),
    CONSTRAINT fk_rp_right FOREIGN KEY (right_entry_id) REFERENCES ledger_entry(id)
);

-- 6. Residual is RECORDED (the operator's affirmative "no counterpart" decision), never
--    derived by subtracting pairings from cluster members. No matched_entry row is written
--    for these entries — they remain available.
CREATE TABLE resolution_unmatched (
    resolution_id   UUID NOT NULL,
    ledger_entry_id UUID NOT NULL,
    CONSTRAINT pk_resolution_unmatched PRIMARY KEY (resolution_id, ledger_entry_id),
    CONSTRAINT fk_ru_res   FOREIGN KEY (resolution_id)   REFERENCES resolution(id),
    CONSTRAINT fk_ru_entry FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry(id)
);
