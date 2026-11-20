# ADR-0023: Phase 1 tenant provisioning bounded contract

- **Status**: Accepted (2026-11-20)
- **Date**: 2026-11-20
- **Decided by**: Boss (explicit Phase 1 build order), `solution-architect` skill
- **Related**: ADR-0020 (direction, implementation deferred), ADR-0016, ADR-0018, ADR-0019, ADR-0021, `docs/01-PRD.md` §3.9/§4/§5/§7, `docs/04-API-SPEC.yaml`

## Context

ADR-0020 locked the onboarding model (OPS-only, admin-provisioned, idempotent,
audited, first ADMIN via endpoint) but deliberately deferred the concrete
shape: paths, DTOs, and error contract were to be decided when the build
trigger fired. On 2026-11-20 Boss ordered the Phase 1 build, excluding Phase 2
self-service (email, anti-abuse, billing, signup UI).

Facts from the repository: `tenants`, `users`, `user_roles`, and `audit_logs`
already exist. `TenantRepository` already exposes `findByOrganizationId` and
`save`. `UserService.create` is tenant-context-bound and cannot be reused
unchanged when the target tenant is not the caller's tenant — the provisioning
service must create the first ADMIN with an explicit `tenantId` instead of
temporarily replacing `TenantContext`.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Least privilege: only global `OPS` may provision | High |
| Idempotency and auditability per ADR-0020 | High |
| No breaking change to existing endpoints or token contract | High |
| No new table; reuse released schema | Medium |
| Operational simplicity for the OPS team | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Three narrow endpoints (create, bootstrap, read-current)** | Each privileged act has its own authorization and audit; idempotency is easy to reason about | Three endpoints to document and test | **Adopted** |
| B. One combined endpoint that creates tenant and ADMIN together | Fewer calls | Conflates two separate privileged acts; partial failure handling becomes ambiguous; idempotency harder to define | Rejected |
| C. Manual SQL only | Zero build cost | Rejected by ADR-0020: no validation, no audit, error-prone | Rejected |

## Decision

**Adopt Alternative A.**

### Endpoints

| Method | Path | Authorization | Success | Contract |
|--------|------|---------------|---------|----------|
| `POST` | `/v1/tenants` | `hasRole("OPS")` | `201` created, `200` idempotent replay | Body: `organizationId` (1–100), `name` (1–100). Response: `TenantResponse` |
| `POST` | `/v1/tenants/{organizationId}/bootstrap-admin` | `hasRole("OPS")` | `201` created, `200` idempotent replay | Body: `username` (1–50), `email`, `password` (≤72). Response: `UserResponse` |
| `GET` | `/v1/tenants/current` | `authenticated()` | `200` | Response: `TenantResponse` of the caller's resolved tenant |

### Rules

- **Idempotency:** replaying `POST /v1/tenants` with the same `organizationId`
  and same `name` returns `200` with the existing row and writes no duplicate.
  A different `name` for the same `organizationId` is a `409`. Replaying
  bootstrap for a tenant that already has an `ADMIN` returns `200` with that
  admin and creates no second one.
- **Validation:** all body fields are validated at the controller with
  `jakarta.validation`. Password strength is checked by `PasswordPolicy`.
- **Audit:** `TENANT_PROVISIONED` carries actor user id plus `organization_id`
  in `details`. Tenant creation uses `recordIndependently` because it has no
  prior audit write in the same transaction. First-ADMIN bootstrap uses
  `record()` (same transaction): `createInTenant` already wrote `USER_CREATED`
  and `ROLES_REPLACED`, so a `REQUIRES_NEW` write would wait for the audit-row
  lock held by its own parent transaction and hang until lock timeout (found
  by QA, 2026-11-20).
- **Tenant context:** provisioning OPS calls carry a valid `X-Organization-ID`
  for the operator's own tenant (for example `org-default`). The new tenant is
  passed in the body/path; `TenantContext` is never replaced. This keeps
  ADR-0019 token-to-header binding intact.
- **Identifier exposure:** the internal `tenant_id` is not returned. Clients
  keep using `X-Organization-ID`; `tenant_id` remains available in JWT claims.
- **Schema:** no new table and no Flyway migration. Existing `tenants`,
  `users`, `user_roles`, and `audit_logs` are reused.
- **Role catalog:** `ADMIN` is the only role bootstrap may assign. `OPS`
  remains non-assignable through any API (ADR-0020, ADR-0021).

### MANAGER role disposition

`MANAGER` is a seeded global role with no `hasRole("MANAGER")` check anywhere
in the codebase. It is **retained for backward compatibility and explicitly
not used for authorization**. Removing it would be a breaking catalog change
(ADR-0021) with no demand; no new authority is attached to it.

## Consequences

**Positive**
- Phase 1 of ADR-0020 becomes executable without touching the token contract.
- Every privileged provisioning act is attributable and idempotent.
- Existing v1.1.x clients are unaffected; only new paths are added.

**Negative / accepted**
- OPS operators must make two calls to onboard a tenant (create, then
  bootstrap). This is deliberate: the acts are separately authorized and
  audited.
- Bootstrap credentials must be handed to the new ADMIN out of band; they are
  never logged or placed in configuration.
- Cross-tenant user creation bypasses `UserService.create` and needs an
  explicit-tenant variant in the application layer, covered by tests.

## Verification

QA stage shall verify: 403 for ADMIN/AUDITOR/USER on both provisioning paths;
201 then 200 idempotent replay for create and bootstrap; 404 for unknown
organization; 409 for conflicting name; `TENANT_PROVISIONED` audit assertion;
cross-tenant isolation of the provisioned tenant; and full `mvn verify` green.
No claim of passing is made until the build is executed.

## Trigger for review

Revisit when Phase 2 self-service is demanded (email, anti-abuse, billing and
threat model must then exist), when Boss reopens the boundary, or when a
breaking role-catalog change such as removing `MANAGER` is explicitly ordered.
