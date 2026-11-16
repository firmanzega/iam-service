-- V12: seed restricted OPS role for operational-only endpoints.
-- The OPS role is intentionally NOT assigned to any user by this migration.
-- Operators create a dedicated OPS user per deployment and assign the role
-- out-of-band (SQL runbook in docs/07-DEPLOYMENT.md). Tenant ADMIN tokens
-- must not see cross-tenant audit-chain metadata, so /v1/audit-logs/verify
-- is restricted to ROLE_OPS instead of ROLE_ADMIN.
INSERT INTO roles (name)
VALUES ('OPS')
ON CONFLICT (name) DO NOTHING;
