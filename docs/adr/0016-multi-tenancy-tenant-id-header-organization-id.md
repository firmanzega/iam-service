# ADR 0016 — Multi-tenancy with internal tenant_id and external X-Organization-ID header

- **Status**: Adopted
- **Date**: 2026-11-14
- **Decided by**: Boss (product/engineering), solution-architect skill

## Context

`iam-service` Package A is a single-organization service. All users, roles, sessions,
refresh tokens, and audit logs share the same database tables without isolation.
The product now needs to serve multiple tenant organizations. Each organization
owns its own users, roles, sessions, and tokens. Client applications may share
one database or have their own internal user structures; the service must
isolate data per organization at the database level.

The external API contract should use business-friendly terminology ("organization"),
while the internal implementation should use architecture-consistent terminology
("tenant"). This avoids leaking architecture jargon to API consumers while keeping
code and schema naming uniform.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Implementation simplicity for small team (< 5 tenants initially) | High |
| Data isolation between organizations | High |
| Minimal migration effort from existing single-tenant schema | High |
| API contract clarity for client applications | Medium |
| Ability to evolve to separate schema/database later | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Shared DB, shared schema, `tenant_id` column (chosen)** | One database, one deployment, simple Flyway migration, easy backup/restore | Logical isolation only; a bug in tenant filtering can leak data across organizations | **Adopted** — fits small team, < 5 tenants, low operational cost |
| B. Separate PostgreSQL schema per tenant | Physical isolation, easier per-tenant backup | Complex Flyway migration across many schemas, higher operational overhead | Rejected — over-engineered for < 5 tenants |
| C. Separate database per tenant | Maximum isolation, clear data ownership | High operational burden, many connection pools, complex backups | Rejected — not justified for current scale |
| D. No database column; filter in application code only | No schema change | Unreliable, easy to bypass, no DB-level safety net | Rejected — unsafe |

## Decision

**We adopt Alternative A.**

Internal implementation (code, database, domain layer):
- Column name: `tenant_id` (UUID or numeric, consistent across all isolated tables)
- Java field: `tenantId` (camelCase)
- Package: `domain/tenant/`, `application/tenant/`, `infrastructure/persistence/tenant/`
- All core tables get `tenant_id`: `users`, `roles`, `sessions`, `refresh_tokens`, `audit_logs`
- Repository layer enforces `WHERE tenant_id = :currentTenantId` on every query
- Role master rows (`ADMIN`, `AUDITOR`, `USER`) remain global (no `tenant_id`) to avoid replication; tenant-specific roles can be added later if needed

External API contract:
- HTTP request header: `X-Organization-ID`
- A `TenantResolver` (filter or interceptor) extracts `X-Organization-ID`, resolves it to an internal `tenant_id`, and stores it in a request-scoped `TenantContext`
- Requests without `X-Organization-ID` on tenant-isolated endpoints receive `401 Unauthorized` (policy defined in ADR-0018: missing or unknown organization is 401 on all paths per the Boss ruling of 2026-11-14, BUG-TENANT-001; unified 2026-11-14)

## Consequences

**Positive**
- Single database, single Flyway migration path; low operational cost
- Consistent internal naming (`tenant_id` everywhere) avoids confusion in code
- Business-friendly external header (`X-Organization-ID`) matches stakeholder language
- Easy to migrate to separate schema/database later if isolation requirements grow

**Negative**
- Logical isolation only; application-level filtering bugs can leak data across tenants (mitigated by repository base class and automated tests)
- All tenants share the same database instance; noisy neighbor possible
- Role master is global; tenant-specific roles require future work

**Rejected alternatives**
- B and C rejected due to operational complexity for < 5 tenants
- D rejected because it provides no database-level safety net

## Verification

- Integration tests must assert that tenant A cannot see tenant B's users, sessions, or tokens (IDOR protection)
- `TenantResolver` unit tests: missing header is reported as MISSING (the filter rejects with 401 on tenant-aware paths), invalid organization returns 401, valid header sets `TenantContext`
- Repository tests assert every query includes `tenant_id` filter
- `ArchitectureLayerTest` remains unchanged; domain layer still has no framework imports

## Trigger for review

- Tenant count exceeds 50 or legal requirement for physical data isolation → reconsider Alternative B or C
- Need for tenant-specific roles or branding → revisit role master strategy

## Related notes

- ADR 0014 introduced per-device sessions; `sessions` table will get `tenant_id`
- docs/03-ARCHITECTURE.md will be updated with tenant layer and `TenantContext` component
- docs/04-API-SPEC.yaml will be updated with `X-Organization-ID` header on isolated endpoints