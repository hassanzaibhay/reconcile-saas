CREATE TABLE IF NOT EXISTS ingestion_run (
    id              UUID         NOT NULL,
    feed_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    content_hash    VARCHAR(64),
    idempotency_key VARCHAR(36),
    row_count       INTEGER,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    CONSTRAINT pk_ingestion_run PRIMARY KEY (id),
    CONSTRAINT chk_ingestion_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS staging_entry (
    id              UUID           NOT NULL,
    ingestion_run_id UUID          NOT NULL,
    entry_date      DATE           NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        CHAR(3)        NOT NULL,
    description     TEXT,
    reference       VARCHAR(255),
    raw_line        TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_staging_entry PRIMARY KEY (id),
    CONSTRAINT fk_staging_run FOREIGN KEY (ingestion_run_id) REFERENCES ingestion_run(id)
);
