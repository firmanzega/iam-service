# ADR-0019: Bind the JWT tenant_id claim to the resolved request tenant

- **Status**: Proposed
- **Date**: 2026-11-16
- **Decided by**: Boss (product/engineering), `solution-architect` skill
- **Related**: ADR-0016, ADR-0018, SEC-TENANT-001 (v1.1.0 hardening)

## Context

ADR-0016 and ADR-0018 partitioned all core tables by `tenant_id` and
established `X-Organization-ID` as the external tenant selector. The JWT
access token carries a `tenant_id` claim (`JwtTokenService`, written since
v1.0.x), but no code path ever **reads** that claim: tenant resolution is
purely header-based. `TenantHeaderFilter` resolves the header to an internal
`tenant_id` and seeds `TenantContext`; every repository query then filters on
that value.

The consequence, documented during the v1.1.0 architecture review: an
attacker (or a confused but legitimate client) holding a valid access token
issued for tenant A needs only change the header value to `org-b` to operate
in tenant B with the token's own roles (including ADMIN). No membership table
exists, and the token claim is never compared. Tenant isolation therefore
holds for data partitioning (repository filters, fail-closed on null context)
but **not** for authorization: the header is attacker-controlled and the
organization identifiers it selects from are not secrets.

This ADR closes that gap with the smallest change that fits the locked
identity model: ADR-0018 scopes each user account to exactly one tenant
(`UNIQUE(tenant_id, username|email)`), so binding the token to the header
tenant is consistent with the released contract. It does **not** introduce
multi-organization membership for a single account; that is deliberately out
of scope (see Alternatives).

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Cross-tenant write/read authorization by a valid token holder | High |
| Zero change to the public client contract (headers, paths, status codes) | High |
| Fit with ADR-0018 (one account = one tenant) | High |
| Implementation risk on the auth hot path | High |
| Ability to evolve to multi-org membership later | Low |
| Operational cost (new tables, backfill, migrations) | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Claim-vs-header equality check (chosen)** | One enforcement point; no schema change; compatible with the released `UNIQUE(tenant_id, …)` identity model; closes the hole completely for one-account-per-tenant | Refresh exchange must stay anchored to the stored token tenant (already the case, ADR-0018 point 6); no support for one account in multiple orgs | **Adopted** |
| B. `tenant_users` membership table + check per request | Supports one account in many organizations; foundation for org switcher UX | New table, migration V12, backfill, new service + query on the request path, new failure modes; solves a requirement that does not exist yet | Rejected for v1.1.0 — revisit via ADR if multi-org membership becomes a real requirement |
| C. Document "tenant = partition only" + treat org-id as secret | Zero work | Org ids are not secret (appear in docs/onboarding); claim of isolation would remain false; useless for audit/compliance | Rejected — not a control |
| D. Bind inside `JwtAuthenticationConverter` | No new filter | Converter has no clean access to the request path for the login/refresh exemption; couples claim validation to authority conversion | Rejected — enforcement placed in a dedicated filter after the bearer filter |

## Decision

**Adopt Alternative A.**

1. A dedicated servlet filter, `TenantBindingFilter`, enforces the binding. It is
   registered in the Spring Security chain **after** `BearerTokenAuthenticationFilter`
   (`addFilterAfter`), so the bearer token has already been validated and the
   security context (and `TenantContext`, seeded earlier by `TenantHeaderFilter`,
   which must run before the bearer filter — see Consequences) is deterministic.
2. Exempt paths: `POST /v1/auth/login` and `POST /v1/auth/refresh`. Both are
   pre-authentication credential-exchange endpoints; refresh remains anchored to
   the tenant stored on the refresh token (ADR-0018 point 6) and ignores the
   header for tenant selection.
3. For every other request carrying an authenticated JWT:
   - If `TenantContext.getTenantId()` is null (e.g. global endpoints reached
     without an organization header) there is nothing to bind — the request
     proceeds under existing rules (fail-closed tenant filters still apply).
   - If the JWT has **no** `tenant_id` claim, or the claim is not a parseable
     long, or claim value ≠ resolved tenant id → the security context is
     cleared and the request is rejected with **401 Unauthorized** in the
     standard `ErrorResponse` shape. Missing claim fails closed.
   - Otherwise the request proceeds.
4. The 401 is recorded via `SecurityEventRecorder.recordUnauthorized(...)`
   with reason `tenant_mismatch` (WARN log + `iam.security.rejected` metric +
   per-minute rejection aggregate). It is not written to `audit_logs`, matching
   the existing policy for 401s.
5. `TenantHeaderFilter` is moved explicitly before `BearerTokenAuthenticationFilter`
   (`addFilterBefore(..., BearerTokenAuthenticationFilter.class)`), making the
   ordering contract between tenant resolution and token validation explicit
   instead of relying on relative order against
   `UsernamePasswordAuthenticationFilter`.
6. Error response message: `"Token does not match the resolved organization"`.
   Status stays 401 (uniform 401 ruling, 2026-11-14); the cause is visible in
   the reason metric, not leaked beyond the client's own knowledge.

## Consequences

**Positive**
- A token issued for tenant A no longer operates on tenant B via header
  tampering. Header tampering degrades from "operate as ADMIN in any org" to
  "401". This also retroactively gives meaning to the `tenant_id` claim that
  was written but unread.
- No database migration, no client contract change, no new dependency.

**Negative / risk**
- Tokens issued before this change always carry `tenant_id` when
  `users.tenant_id` is non-null (guaranteed by V9 `NOT NULL`), so no
  grandfathering window is required; existing tokens stay valid.
- The auth hot path gains one claim parse and one comparison per authenticated
  request — negligible (string → long, no I/O).
- Enforcement correctness now depends on filter ordering in the security
  chain; covered by an integration test that asserts 401 on
  header ≠ token and 200/201 on match, plus a unit-level ordering note in
  `SecurityConfig`.
- A user legitimately entitled to two organizations still cannot use one
  account for both; accepted (ADR-0018 identity model) and documented.

**Accepted debt / future work**
- `tenant_users` membership table (Alternative B) if multi-org single-account
  becomes a product requirement — new ADR, not an incremental tweak.
- Audit chain verification remains global (see v1.1.0 scope item:
  ops-only verify); per-tenant chains are a separate future ADR.

## Trigger for review

- Product requests one account operating across organizations → revisit
  Alternative B as ADR-0020.
- Tenant count or compliance regime changes materially (e.g. regulator demands
  per-tenant verification) → revisit audit-chain design.

## Verification

- `TenantBindingIntegrationTest` (new): token(org-a) + header `org-b` → 401 on
  an ADMIN endpoint (`/v1/users`); same token + header `org-a` → 200; refresh
  of a stored org-a token with header `org-b` → succeeds anchored to org-a
  (ADR-0018 point 6); authenticated endpoint without header → 401 via existing
  rule; token without `tenant_id` → 401.
- `SecurityConfigTest`/context test asserts bean wiring and filter order.
- Full `mvn verify` green; JaCoCo per-class gate holds.

## Related notes

- ADR-0016 (shared schema), ADR-0018 (tenant-scoped identity, header-based
  resolution and the explicit non-use of the claim — **superseded in part by
  this ADR**: the claim is now authoritative for the binding check)
- `docs/WORKLOG.md` (v1.1.0 execution log)
- `07-DEPLOYMENT.md` §9 residual risks (updated in v1.1.0)
