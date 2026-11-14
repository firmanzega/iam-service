-- Tenant-scoped 401 rejection aggregate uniqueness (SEC-STATS-001, Boss ruling 2026-11-14).
--
-- Why: V8 created a GLOBAL unique constraint on (window_start, http_method,
-- reason) for security_rejection_stats, before tenants existed. V9 added
-- tenant_id but never re-scoped that constraint, so two tenants rejected
-- 401 in the same minute for the same method and reason collided: the second
-- tenant's INSERT violated the global unique, the recorder swallowed the
-- exception, and that tenant's 401 aggregate was silently lost.
--
-- Safe: every existing row is already unique on the global key, so no
-- duplicate can exist under the composite key either. Rollback: restore the
-- global unique ONLY before a second tenant is onboarded
-- (see docs/07-DEPLOYMENT.md); afterwards the global constraint is
-- unsatisfiable.

ALTER TABLE security_rejection_stats
    DROP CONSTRAINT uq_security_rejection_stats;

ALTER TABLE security_rejection_stats
    ADD CONSTRAINT uq_security_rejection_stats_tenant
    UNIQUE (tenant_id, window_start, http_method, reason);

COMMENT ON CONSTRAINT uq_security_rejection_stats_tenant ON security_rejection_stats IS
    'Per-tenant aggregate uniqueness (SEC-STATS-001): one row per tenant per minute per method per reason.';
