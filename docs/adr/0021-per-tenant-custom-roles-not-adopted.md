# ADR-0021: Per-tenant custom roles — evaluated and not adopted; tenant applications self-manage their permissions

- **Status**: Accepted (2026-11-16) — decision recorded; revisit only via the trigger below
- **Date**: 2026-11-16
- **Decided by**: Boss (product/engineering), `solution-architect` skill
- **Related**: ADR-0016 (multi-tenancy, global roles), ADR-0018 (tenant-scoped identity, roles remain global), ADR-0019 (tenant binding), `docs/01-PRD.md` §4, §7

## Context

The question on the table during the 2026-11-16 cloud-roadmap discussion:
should tenants be allowed to define their **own** roles with tenant-specific
permission bundles (e.g. `MANAGER_CABANG`), enforced by iam-service — instead
of the current global role set (`ADMIN`, `AUDITOR`, `USER`, `MANAGER`, `OPS`)?

Why this is bigger than it looks: enforcement in iam-service lives in
`@PreAuthorize`/filter expressions over Spring Security authorities. A
tenant-defined role name with no meaning in code authorizes nothing. Safe
per-tenant custom roles therefore require a **global permission catalog
defined in code**, built-in roles refactored as bundles of that catalog, and
per-tenant custom roles as new bundles — a full authorization-model change, not
an additive feature.

Boss initially leaned toward supporting this ("tenant-side admins will manage
their data"), then, after the analysis below, ruled: keep the existing model —
iam-service stays **identity + global RBAC**; permissions specific to each
tenant's application are managed **in the tenant's own application**, not in IAM.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Security correctness of any custom authorization (no untethered tenant-authored permissions) | High |
| Blast radius of the change (all endpoints, all integration tests, both deployment models) | High |
| Evidence of real demand (no tenant has asked yet; cloud is exploration) | High |
| Tenant autonomy for application-specific authorization | Medium |
| Operational and migration simplicity | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Keep global roles; tenant apps self-manage app permissions (chosen)** | Zero refactor; enforcement stays simple and reviewable; identity boundary stays clean (IAM = who you are + global RBAC; app = what you may do inside it) | Tenant apps must implement their own permission logic for app-specific capabilities | **Adopted** |
| B1. Global permission catalog + per-tenant custom role bundles (`permissions`, `role_permissions`, `roles.tenant_id NULL = built-in / set = custom`, `@PreAuthorize("hasAuthority('...')")`) | The only sound shape of per-tenant custom roles; one mechanism, two role kinds | Large refactor (every `hasRole(...)` site), migration + backfill, new CRUD surface, new failure modes, QA matrix regrowth; justified only by real demand | Deferred — revisit via trigger |
| B2. Tenant-authored free-text permission strings | Trivially flexible | Permissions unmapped to any check authorize nothing (or worse, everything); unreviewable | Rejected permanently |
| B3. Custom role names as aliases mapped to one global role | Cheap, no refactor | Cosmetic naming only; adds no capability; creates false expectations | Rejected |
| B4. Enrich the global role list (`SUPERVISOR`, `MANAGER2`, ...) | No model change | Cartesian-product role explosion; not per-tenant; recurring change requests | Rejected |
| B5. Status quo with no documentation (implicit) | Zero work | Decision undocumented; risk of re-litigating or accidental scope creep later | Rejected — this ADR is the record |

## Decision

**Adopt Alternative A.** iam-service keeps its global roles:

- `ADMIN`, `AUDITOR`, `USER`, `MANAGER` — tenant-relevant, assignable by a
  tenant's own ADMIN, tenant-scoped via `user_roles` (assignments follow the
  owning user, ADR-0016/0018).
- `OPS` — global, cross-tenant, deployment-operator only (V12; see
  `docs/07-DEPLOYMENT.md` §13).

No per-tenant custom role or custom permission capability shall be added to
this service. Authorization for capabilities inside a tenant's own application
(e.g. which branch manager may approve what) is out of scope for this IAM
service and shall be implemented by that application, using the identity and
global-role claims this service issues.

## Consequences

**Positive**
- No authorization-model refactor; the v1.1.0 security posture is untouched.
- Clear service boundary: iam-service answers *who you are* and *your global
  role*; tenant applications answer *what you may do inside the application*.

**Negative / accepted limitation**
- Tenants needing fine-grained, application-specific permissions must build
  them in their own application — IAM will not absorb that variance.
- If a strong customer requirement for first-class per-tenant custom roles
  appears later, this decision will have to be revisited explicitly (see
  trigger), with B1 as the known viable shape.

**Rejected alternatives** — see table above; B2 is rejected permanently.

## Trigger for review

Revisit this ADR only when **at least one** holds:

- A concrete tenant/customer contractually requires tenant-defined roles with
  custom permission bundles that cannot be expressed as a mapping to the
  global set, or
- ≥ 2 independent tenants request the same non-standard permission shape, or
- Boss explicitly reopens the question.

If reopened, adopt the B1 shape **catalog-first**: (1) introduce the global
permission catalog and refactor enforcement to authorities (no contract
change), (2) ship built-in roles as catalog bundles (no contract change),
(3) only then add per-tenant custom role CRUD (new contract; new ADR).

## Verification

Not applicable — no code changes result from this decision. The guarantee is
documentary: global roles unchanged; no new `@PreAuthorize` authorities are
introduced; `TenantIsolationIntegrationTest` and authorization matrix tests
remain the enforcement proof.

## Related notes

- `docs/01-PRD.md` §4 (known limitations), §7 (open questions) — updated 2026-11-16.
- ADR-0018 point 4 (roles remain global), ADR-0019 (binding).
- Cross-tenant audit verification remains global and `OPS`-only; per-tenant
  audit chains are a separate future ADR (already noted in ADR-0019).
