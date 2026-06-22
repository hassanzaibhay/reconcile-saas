CREATE TABLE IF NOT EXISTS match_run (
    id            UUID        NOT NULL,
    rule_set_id   VARCHAR(255),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    matched_count INTEGER     NOT NULL DEFAULT 0,
    unmatched_count INTEGER   NOT NULL DEFAULT 0,
    CONSTRAINT pk_match_run PRIMARY KEY (id),
    CONSTRAINT chk_match_run_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS match_result (
    id            UUID        NOT NULL,
    match_run_id  UUID        NOT NULL,
    left_entry_id UUID        NOT NULL,
    right_entry_id UUID       NOT NULL,
    rule_id       VARCHAR(255) NOT NULL,
    matched_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_result PRIMARY KEY (id),
    CONSTRAINT fk_match_run FOREIGN KEY (match_run_id) REFERENCES match_run(id)
);

CREATE TABLE IF NOT EXISTS discrepancy (
    id           UUID        NOT NULL,
    match_run_id UUID        NOT NULL,
    entry_id     UUID        NOT NULL,
    reason       VARCHAR(50) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_discrepancy PRIMARY KEY (id),
    CONSTRAINT fk_disc_run FOREIGN KEY (match_run_id) REFERENCES match_run(id)
);

CREATE TABLE IF NOT EXISTS audit_decision (
    id          UUID         NOT NULL,
    match_run_id UUID        NOT NULL,
    rule_id     VARCHAR(255) NOT NULL,
    entry_id    UUID         NOT NULL,
    decision    VARCHAR(20)  NOT NULL,
    reason      TEXT,
    decided_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_by  VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT pk_audit_decision PRIMARY KEY (id),
    CONSTRAINT chk_decision CHECK (decision IN ('MATCHED', 'UNMATCHED'))
);

CREATE INDEX idx_audit_run ON audit_decision (match_run_id);
