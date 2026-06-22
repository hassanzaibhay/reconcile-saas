CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID         NOT NULL,
    actor       VARCHAR(255) NOT NULL,
    action      VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   VARCHAR(255),
    payload     JSONB,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at);
