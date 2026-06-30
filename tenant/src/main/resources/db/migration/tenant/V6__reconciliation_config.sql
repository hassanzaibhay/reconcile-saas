CREATE TABLE reconciliation_config (
    singleton            BOOLEAN       PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    absolute_tolerance   NUMERIC(19,4) NOT NULL DEFAULT 0.01,
    percentage_tolerance NUMERIC(8,6)  NOT NULL DEFAULT 0.001,
    max_date_drift_days  INT           NOT NULL DEFAULT 0,
    axis                 VARCHAR(20)   NOT NULL DEFAULT 'SUM_TO_ZERO'
                          CHECK (axis IN ('SUM_TO_ZERO','DIFFERENCE')),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO reconciliation_config (singleton) VALUES (TRUE);
