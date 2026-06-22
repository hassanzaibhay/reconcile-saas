CREATE TABLE IF NOT EXISTS ledger_entry (
    id            UUID           NOT NULL,
    feed_id       VARCHAR(255)   NOT NULL,
    entry_date    DATE           NOT NULL,
    amount        NUMERIC(19, 4) NOT NULL,
    currency      CHAR(3)        NOT NULL,
    description   TEXT,
    reference     VARCHAR(255),
    ingestion_run_id UUID,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id)
);

CREATE INDEX idx_ledger_entry_date    ON ledger_entry (entry_date);
CREATE INDEX idx_ledger_entry_feed_id ON ledger_entry (feed_id);
