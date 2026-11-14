-- Tenant-scoped identity (ADR-0018, BUG-TENANT-004).
--
-- Why: username/email must be unique per tenant, not globally, so cloud
-- deployments (shared schema, many organizations) can hold identical
-- identities in different tenants. On-premise keeps working because it
-- runs a single tenant (org-default) per installation.
--
-- Safe constraint lookup across names: V1 created UNIQUE inline, so
-- PostgreSQL auto-generated the constraint names (e.g. users_username_key).
-- The DO block finds the single-column UNIQUE constraints on
-- username/email regardless of name and replaces them with composite
-- tenant-scoped unique constraints.
--
-- Rollback: restore from backup, or manually drop the two composite
-- constraints and recreate UNIQUE on username and email (only safe when
-- no cross-tenant duplicate identities exist).

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = 'users'
          AND c.contype = 'u'
          AND array_length(c.conkey, 1) = 1
          AND (SELECT a.attname
                 FROM pg_attribute a
                WHERE a.attrelid = t.oid
                  AND a.attnum = c.conkey[1]) IN ('username', 'email')
    LOOP
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', constraint_record.conname);
    END LOOP;
END $$;

ALTER TABLE users
    ADD CONSTRAINT users_tenant_username_key UNIQUE (tenant_id, username);

ALTER TABLE users
    ADD CONSTRAINT users_tenant_email_key UNIQUE (tenant_id, email);

COMMENT ON CONSTRAINT users_tenant_username_key ON users IS
    'Tenant-scoped identity (ADR-0018): username unique within one tenant.';
COMMENT ON CONSTRAINT users_tenant_email_key ON users IS
    'Tenant-scoped identity (ADR-0018): email unique within one tenant.';
