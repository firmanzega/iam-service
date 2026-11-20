# ADR-0020: Tenant provisioning via OPS-only tooling (Phase 1, admin-provisioned onboarding)

- **Status**: Accepted (direction locked; implementation deliberately deferred until the trigger below is met)
- **Date**: 2026-11-16
- **Decided by**: Boss (product/engineering), `solution-architect` skill, recorded with `senior-business-analyst`
- **Related**: ADR-0016 (multi-tenancy), ADR-0018 (tenant-scoped identity), ADR-0019 (tenant binding), `docs/01-PRD.md` §4, §7

## Context

The product is marketed for both on-premise (single `org-default` install) and
shared multi-tenant cloud deployments (ADR-0018). Today, creating a tenant is a
**manual SQL operation**: insert a row into the `tenants` registry
(`tenant_id`, external `organization_id`, name), then bootstrap the first
tenant ADMIN. There is no endpoint, CLI, or migration path for this.

As of 2026-11-16 the cloud direction is still **exploration**: no concrete
tenant candidate exists yet. During the cloud-roadmap discussion (2026-11-16)
Boss locked the onboarding model for the first phase:

- **Phase 1 (now): admin-provisioned.** When a new tenant (organization)
  appears, the OPS operator team (not a lone individual) creates the tenant
  and provisions its first ADMIN. Tenant ADMINs then run self-sufficiently
  (create users, assign the global roles, etc.).
- **Phase 2 (later, gated by evidence): self-service signup.** Only if real
  demand appears (see Trigger for review). Requires email infrastructure,
  anti-abuse controls, billing/plan decisions, and a threat model — none of
  which exist today.

Boss's explicit ruling on the bootstrap step: the first tenant ADMIN should be
created **via an endpoint after the tenant exists**, not via manual SQL —
quoting the reasoning that endpoint-based creation is the better practice
because it avoids mistyped manual SQL and can be validated and audited.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Correctness and auditability of tenant bootstrap (no unjustified manual SQL in the hot path of onboarding) | High |
| Least privilege: only deployment operators may provision tenants | High |
| Fit with the locked identity model (ADR-0018) and global OPS role (V12) | High |
| Build only what has demand (no speculative self-service surface) | High |
| Reusability: the Phase 1 provisioning path must be reusable by Phase 2 self-service | Medium |
| Operational simplicity for a small operator team (shared responsibility, no single point of failure) | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. OPS-only provisioning endpoint / tooling, built when the first real tenant triggers it (chosen)** | Validated input, idempotent, audited via `recordIndependently` (V6 pattern); least-privilege (global `OPS` only, V12); reusable as the backend of Phase 2 signup; no tenant may self-provision | Not built yet; requires its own ADR-gated build phase (endpoint, DTOs, tests, API-SPEC, runbook) | **Adopted as direction** |
| B. Continue manual SQL provisioning indefinitely | Zero build cost | No validation, no audit trail, error-prone (wrong `tenant_id` linkage, silent misconfiguration); contradicts Boss's explicit ruling | Rejected |
| C. Self-service tenant signup now | Product-led onboarding | Requires email infra (absent), anti-abuse (absent), billing decision (absent), threat model (not done); no demand evidence; builds an abuse surface with no attacker yet | Rejected for now — Phase 2 gate |
| D. Infrastructure-as-code / templated deployment per tenant (one deployment per tenant) | Strong physical isolation | Violates ADR-0016 (shared schema chosen); per-tenant ops cost; explicitly not the chosen cloud model | Rejected |

## Decision

**Adopt Alternative A, implemented as a deferred Phase 1 build.**

1. Onboarding stays **admin-provisioned** by the OPS operator team (multiple
   operators, not a single person — bus factor mitigation agreed 2026-11-16).
   On-premise installs may bootstrap via DBA-executed SQL per
   `docs/07-DEPLOYMENT.md` §13 (existing, accepted procedure).
2. When the build trigger fires, the provisioning capability shall be:
   - an **OPS-only** surface (global `OPS` role, V12); no tenant role, and no
     `ADMIN`, may provision tenants;
   - **idempotent** (re-running with the same tenant parameters must not
     create duplicates or corrupt state);
   - **validated and audited** (`TENANT_PROVISIONED` audit action with actor
     and tenant identifiers, recorded independently of the request
     transaction);
   - the bootstrap of the first tenant ADMIN shall be performed **through the
     endpoint, after the tenant exists** — never via ad-hoc manual SQL;
   - tenant ADMIN self-sufficiency afterwards is already supported by the
     released user-management surface.
3. The concrete shape (paths, DTOs, error contract) is intentionally **not**
   decided here. It will be specified in a follow-up revision of this ADR or a
   successor ADR (number to be assigned when the build is triggered) and in `docs/04-API-SPEC.yaml` **at the time the build
   is triggered**, together with tests and a QA gate.
4. No self-registration, no email flows, no signup UI shall be built as part
   of this decision.

## Consequences

**Positive**
- Onboarding errors become detectable and attributable (audit), not silent.
- Least privilege is preserved: provisioning is a deployment-operator concern,
  never a tenant concern.
- Phase 2 self-service, if ever adopted, reuses this path instead of forking it.

**Negative / accepted debt**
- Until the build is triggered, onboarding remains manual (SQL on on-premise;
  manual steps in cloud) — accepted explicitly because no concrete tenant
  exists yet.
- The OPS team (not a lone operator) must have a documented grant/revoke and
  handover procedure (`docs/07-DEPLOYMENT.md` §13); this ADR does not replace it.
- Provisioning will create the most privileged artifacts in the system
  (tenant + first ADMIN); the build phase must include a security review.

**Rejected alternatives** — see table above.

## Trigger for review

Build this ADR when **any** of the following is true (whichever first):

- A concrete cloud tenant candidate commits to onboarding (paid or LOI), or
- The operator team performs more than a small number of manual onboardings
  and requests tooling, or
- Boss explicitly orders the Phase 1 build.

Review Phase 2 (self-service) only when **both** hold:

- ≥ 1 real tenant/customer rejects manual onboarding or multiple prospects
  request self-service, and
- Email infrastructure + anti-abuse decisions are made (see `docs/01-PRD.md` §7).

## Verification

Not applicable yet — nothing is implemented. When built, verification shall
require: integration tests for OPS-only access (403 for non-OPS), idempotency
test, audit action assertion, cross-tenant isolation test on the provisioned
tenant, and a full `mvn verify` green plus QA gate per `team-workflow`.

## Related notes

- `docs/01-PRD.md` §4 (known limitations), §7 (open questions) — updated 2026-11-16.
- `docs/07-DEPLOYMENT.md` §13 — OPS account operations (break-glass).
- ADR-0018 point 7 (on-premise default `org-default`), ADR-0019 (tenant binding).
