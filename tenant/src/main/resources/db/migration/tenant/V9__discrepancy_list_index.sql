-- V9: Supporting indexes for the discrepancy list keyset query (sub-slice 2).
-- =============================================================================
-- Non-concurrent CREATE INDEX takes ACCESS EXCLUSIVE on `discrepancy` for the build duration.
-- Fine on a fresh/empty tenant schema at provisioning time. Runs against every existing tenant on
-- release too (per the invariant that tenant migrations apply atomically), which briefly blocks
-- writes on that tenant's discrepancy table if it already holds data. Acceptable at this slice's
-- scale; at higher volume this needs CONCURRENTLY + a non-transactional Flyway migration instead.

CREATE INDEX idx_disc_created_id     ON discrepancy (created_at DESC, id DESC);
CREATE INDEX idx_disc_run_created_id ON discrepancy (match_run_id, created_at DESC, id DESC);
