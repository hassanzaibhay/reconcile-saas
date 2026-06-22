CREATE TABLE IF NOT EXISTS public.tenants (
    id         UUID        NOT NULL,
    slug       VARCHAR(63) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEPROVISIONED'))
);
