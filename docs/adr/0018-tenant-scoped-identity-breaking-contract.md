# ADR-0018: Tenant-scoped identity and required X-Organization-ID on public auth endpoints

- **Status**: Accepted
- **Date**: 2026-11-14
- **Decided by**: Boss (product/engineering), `solution-architect` skill

## Context

`iam-service` shipped Package A (v1.0.0, 2026-11-14) with two multi-tenancy
decisions locked by ADR-0016: a shared schema with a `tenant_id` column on
core tables, and an external `X-Organization-ID` header that resolves to an
internal `tenant_id`.

That design left identity unscoped: `users.username` and `users.email` were
globally `UNIQUE`, and `findByUsernameOrEmail` matched on `username = ? OR
email = ?` with no tenant predicate — while repository comments and ADR-0016
verification claimed tenant isolation in user lookup (BUG-TENANT-004, recorded
in Worklog and `06-TEST-PLAN.md`).

The product is marketed for both **on-premise** single-tenant deployments
(one `org-default` per install) and **cloud** shared deployments (many
customers on one shared schema). Global-unique identity is an acceptable
choice only for on-premise; shared deployments need per-tenant namespaces so
that `admin` may exist in `org-default` and in `org-b` simultaneously.

This ADR records the accepted breaking contract change and its consequences.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Correctness of tenant isolation for cloud (no cross-tenant identity leakage) | High |
| Ability to offer identical on-premise and cloud builds from one artifact | High |
| Simplicity of the login contract (minimal client changes) | High |
| Migration risk at current pre-release state | High |
| Fit with ADR-0016 (shared schema) | Medium |
| Backward compatibility | Low (pre-release, accepted) |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Tenant-scoped identity + required header (chosen)** | Correct for cloud (no cross-tenant collision); one artifact for both deployment models; clean tenant resolution at login | Breaking change: clients must send `X-Organization-ID` on `/v1/auth/login` and `/v1/auth/refresh`; update PRD/API-SPEC/tests | **Adopted** — fits on-premise + cloud strategy |
| B. Global unique identity (status quo) | Zero breaking change; trivial client integration | Cannot host two organizations with identical identifiers on shared schema; inconsistent with ADR-0016 verification | Rejected — fails cloud marketing |
| C. Tenant schema per tenant | Perfect physical isolation | Rejected in ADR-0016 Alternative B — operational overhead per tenant | Rejected — not needed at current scale |
| D. Email global unique + username tenant-scoped | Mixed isolation | Asymmetric identity semantics; confusing failure modes at login | Rejected |

## Decision

**Adopt Alternative A.**

1. `users.username` and `users.email` become per-tenant unique:
   `UNIQUE(tenant_id, username)` and `UNIQUE(tenant_id, email)`. The global
   unique constraints from V1 are dropped; V10 performs this migration.
2. `X-Organization-ID` is **required** on the only public authentication
   endpoints:
   - `POST /v1/auth/login`
   - `POST /v1/auth/refresh`
   Missing or unknown organization → **401 Unauthorized**. This reflects the
   Boss ruling of 2026-11-14 (BUG-TENANT-001): an unknown or absent
   organization must be rejected, never silently fall back to `org-default`.
   Note the deliberate asymmetry: missing header on `/v1/auth/login` and
   `/v1/auth/refresh` is 401 (public endpoints, no credential exists yet to
   bind to an organization), while missing header on the tenant-scoped
   session endpoints (`/v1/auth/logout`, `/v1/auth/sessions*`) remains
   400 Bad Request as locked in Stage 4/6 with green tests. Unknown
   organization is 401 **everywhere**.
3. Tenant-scoped lookups for login verify: `WHERE tenant_id = :tenantId AND
   (username = :identifier OR email = :identifier)`. The internal
   authentication path is unambiguous.
4. Role names (`ADMIN`, `AUDITOR`, `USER`) remain global per ADR-0016; role
   assignments in `user_roles` remain tenant-pure via the owning user.
5. `X-Organization-ID` remains required on `/v1/auth/logout`,
   `/v1/auth/sessions`, `/v1/auth/sessions/{sessionId}` (ADR-0016).
6. JWT `tenant_id` claim (added in Stage 4) is carried but **not** used as
   the resolution source; resolution is header-based because public clients
   need a deterministic way to pick a tenant before any
   shared credential exists. Refresh exchange relies on the refresh token's
   stored tenant, not on the header, to avoid double-standard behavior
   (see Stage 4: both paths verified in tests).
7. On-premise deployments default `X-Organization-ID` to `org-default` in the
   deployment config; cloud clients must pass their organization id.

## Consequences

**Positive**
- Identical artifact for on-premise and cloud; tenant isolation is complete on
  identity.
- No cross-tenant credential ambiguity; eliminates BUG-TENANT-004.
- Login latency remains unchanged; one extra header parse on the public path.

**Negative / migration risk**
- **Breaking contract**: existing clients must send `X-Organization-ID`.
  Deployment checklist updated in `07-DEPLOYMENT.md`.
- V10 migration drops and recreates unique indexes on `users` (safe at
  pre-release; rollback path: restore from backup — `07-DEPLOYMENT.md`).
- Tests asserting 200/201 on login without header must be updated (Stage 4).

**Accepted debt / future work**
- Refresh endpoint accepting refresh tokens without tenant marker: resolved by
  storing `tenant_id` on `refresh_tokens` (done in V9/Stage 4); documented
  here so reviewers do not revert it.
- Optional `X-Organization-ID` on login (on-premise default) is **not**
  adopted; explicit-and-identical behavior across deployment models is
  preferred over hidden defaults (ADR-0016 verification).

## Trigger for review

- Tenant count exceeds 50 **or** a legal/compliance requirement for physical
  isolation is issued → reconsider Alternative B or C for ADR-0016.
- First on-premise enterprise customer requests per-instance role separation
  → revisit role isolation (currently global).
- Public API deprecation policy adopted → 2.0.0 may absorb any further
  contract restructuring.

## Verification

- ADR-0016 verification still holds and is extended: every core table has
  `tenant_id`; every tenant-scoped query includes it (Stage 6).
- `TenantIsolationIntegrationTest` covers login + refresh + sessions across
  two tenants (extended in Stage 4).
- API contract enforced by filter integration tests (Stage 4).

## Related notes

- ADR-0016 (multi-tenancy decision, alternatives B/C rejected)
- ADR-0017 (structured logging)
- `06-TEST-PLAN.md` §10 (QA findings BUG-TENANT-001..004)
- `WORKLOG.md` (current stage and open decisions)
