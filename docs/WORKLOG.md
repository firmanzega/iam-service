# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 3 doc sync (ADR-0016/0018 + ARCHITECTURE aligned to Boss's BUG-TENANT-001 ruling); Stage 4 - Backend Fixes done earlier today (ADR-0018 implemented, 257/257 green, committed d16d692)
- **Next Active Stage:** Stage 6 - QA Retest (per Boss) -> Stage 7 - Release Prep
- **Active Skill to Load:** `senior-qa-engineer`
- **Last Updated:** 2026-11-14 14:55

## 1. Key Decisions & Locked Artifacts
- **Stage 7 (Security & Release):** `docs/07-DEPLOYMENT.md` and `CHANGELOG.md` v1.0.0 completed (committed); Package A conditional GO pending BUG-TENANT-001/004 resolution.
- **Stage 3 (Architecture):** Locked ADR-0016 (`tenant_id` / `X-Organization-ID`) and ADR-0017 (structured logging) in commit `638c1d3`; ADR-0018 (tenant-scoped identity) in `f88f6fa`. Scenario D (self-service) deferred.
- **Stage 4 & 6 (Implementation & QA):** Applied `V9__add_tenant_id.sql`, `TenantHeaderFilter`, and tenant-scoped repos (`63814ac`); fixed BUG-TENANT-002/003 (`8f125e8`); 249/249 tests green (`29ea651`).
- **Stage 4 (Backend, 2026-11-14):** Implemented ADR-0018. `V10__tenant_scoped_identity.sql` (composite `UNIQUE(tenant_id, username|email)`); login/refresh now require `X-Organization-ID` (missing/unknown -> 401); refresh flow scoped to stored refresh-token tenant (ADR-0018 point 6); unknown organization -> 401 on all paths (BUG-TENANT-001 fix, Boss-approved); **login lookup query fixed**: derived `findByUsernameOrEmail` ignored `tenantId` entirely (non-unique result across tenants) -> explicit `@Query` with tenant predicate (BUG-TENANT-004). JWT `tenant_id` claim already carried (JwtTokenService), used for display/claims only, not resolution. CORS now allows `X-Organization-ID`. 257/257 green.
- **Stage 1 (PRD):** PRD updated for multi-tenancy + structured logging (committed in f88f6fa).

## 2. Open Questions / Blockers for User
1. **RESOLVED (2026-11-14):** missing `X-Organization-ID` on login/refresh stays **401** (Boss: "sesuai informasi yang udah pernah gue pilih untuk BUG TENANT-001 ... reject 401"). Sessions/logout missing also unified to **401** on 2026-11-14 (Boss: "disatukan saja 401"); unknown org is 401 everywhere. `03-ARCHITECTURE.md`, `ADR-0016`, `ADR-0018`, API-SPEC, and code all synced; missing-vs-unknown cause is carried by the error body.
2. **BUG-TENANT-001:** Fixed in Stage 4, pending QA retest (Stage 6).
3. **BUG-TENANT-004:** Fixed in Stage 4 (V10 + query predicate + composite unique), pending QA retest (Stage 6).
4. **JWT Claim:** `tenant_id` claim present; resolution stays header-based per ADR-0018 point 6. Stage 6/7: verify nothing reads the claim for authorization decisions.
5. **Release version for breaking contract:** `1.1.0` or `2.0.0`? Decision needed at release time (see PRD §6).
6. **On-premise default:** Confirm single `org-default` tenant per on-premise install; cloud shares schema (ADR-0016 Alternative A).

## 3. Exact Next Action (Start Here in New Session)
1. Load skill `senior-qa-engineer`.
2. Read `/docs/06-TEST-PLAN.md` §10 (BUG-TENANT-001..004), the Stage 4 commit, and this WORKLOG.
3. QA retest: login/refresh without + with unknown `X-Organization-ID` expect 401; same identity in two tenants both log in; refresh with mismatched header keeps original session; full suite green.
4. Close BUG-TENANT-001/004 in `06-TEST-PLAN.md`, then proceed to Stage 7 (release prep) per Boss: version bump (open question 5), CHANGELOG, deployment checklist (V10 rollback path).

## 4. Decision & Switch Log
- [2026-11-14 | Stage 3 doc sync] Boss ruling (BUG-TENANT-001): reject 401 — unknown organization is 401 on all paths; missing/unknown org unified to 401 on all tenant-aware paths (Boss: "disatukan saja 401"); error body carries missing-vs-unknown cause. `03-ARCHITECTURE.md`, `ADR-0016`, `ADR-0018` rationale, API-SPEC, TenantHeaderFilter, and tests synced. — Reason: Boss ruling, 2026-11-14.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 migration, required header on login/refresh (401), refresh anchored to stored token tenant, BUG-TENANT-001 (unknown org -> 401 everywhere) and BUG-TENANT-004 (query predicate) fixed; login lookup was leaking across tenants pre-fix. CORS preflight allows X-Organization-ID. — Reason: Boss decision Option (a), 2026-11-14.
- [2026-11-14 | Stage 6 -> 4 -> 6] Fixed BUG-TENANT-002 (`requestURI` filter under MockMvc) and BUG-TENANT-003 (`user.getTenantId()` session attribution) in commit `8f125e8` — Reason: QA retest failures.
- [2026-11-14 | Stage 1] PRD updated for multi-tenancy + structured logging (check-id/check-bahasa clean); Boss approved Option (a) credit: marketing on-premise + cloud. — Reason: Boss decision, 2026-11-14.
- [2026-11-14 | Stage 3] ADR-0018 created: tenant-scoped identity, required `X-Organization-ID` on `/v1/auth/login` + `/v1/auth/refresh` (401 on unknown/missing org per ADR-0016 verification). — Reason: Boss decision, Option (a).
