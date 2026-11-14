# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 6 - QA Retest (PASS with 1 escalation QA-GATE-001: JaCoCo per-class gate red on RejectionStatRow; 301/301 green, committed)
- **Next Active Stage:** Stage 7 - Release Prep (blocked on QA-GATE-001 backend fix; version bump 1.1.0 vs 2.0.0 pending Boss)
- **Active Skill to Load:** `senior-backend-developer` (QA-GATE-001) -> `senior-security-engineer` (Stage 7)
- **Last Updated:** 2026-11-14 15:15

## 1. Key Decisions & Locked Artifacts
- **Stage 7 (Security & Release):** `docs/07-DEPLOYMENT.md` and `CHANGELOG.md` v1.0.0 completed (committed); Package A conditional GO pending BUG-TENANT-001/004 resolution.
- **Stage 3 (Architecture):** Locked ADR-0016 (`tenant_id` / `X-Organization-ID`) and ADR-0017 (structured logging) in commit `638c1d3`; ADR-0018 (tenant-scoped identity) in `f88f6fa`. Scenario D (self-service) deferred.
- **Stage 4 & 6 (Implementation & QA):** Applied `V9__add_tenant_id.sql`, `TenantHeaderFilter`, and tenant-scoped repos (`63814ac`); fixed BUG-TENANT-002/003 (`8f125e8`); 249/249 tests green (`29ea651`).
- **Stage 4 (Backend, 2026-11-14):** Implemented ADR-0018. `V10__tenant_scoped_identity.sql` (composite `UNIQUE(tenant_id, username|email)`); login/refresh now require `X-Organization-ID` (missing/unknown -> 401); refresh flow scoped to stored refresh-token tenant (ADR-0018 point 6); unknown organization -> 401 on all paths (BUG-TENANT-001 fix, Boss-approved); **login lookup query fixed**: derived `findByUsernameOrEmail` ignored `tenantId` entirely (non-unique result across tenants) -> explicit `@Query` with tenant predicate (BUG-TENANT-004). JWT `tenant_id` claim already carried (JwtTokenService), used for display/claims only, not resolution. CORS now allows `X-Organization-ID`. 257/257 green.
- **Stage 1 (PRD):** PRD updated for multi-tenancy + structured logging (committed in f88f6fa).

## 2. Open Questions / Blockers for User
1. **RESOLVED (2026-11-14):** missing `X-Organization-ID` on login/refresh stays **401** (Boss: "sesuai informasi yang udah pernah gue pilih untuk BUG TENANT-001 ... reject 401"). Sessions/logout missing also unified to **401** on 2026-11-14 (Boss: "disatukan saja 401"); unknown org is 401 everywhere. `03-ARCHITECTURE.md`, `ADR-0016`, `ADR-0018`, API-SPEC, and code all synced; missing-vs-unknown cause is carried by the error body.
2. **BUG-TENANT-001:** FIXED + QA-verified 2026-11-14 (401 on every path).
3. **BUG-TENANT-004:** FIXED + QA-verified 2026-11-14 (V10 composite unique + explicit tenant predicate; two-tenant same-identity login passes).
4. **QA-GATE-001 (new):** `mvn clean verify` fails per-class JaCoCo gate on `SecurityRejectionStatPersistenceAdapter.RejectionStatRow` (0.66 < 0.80; dead getters). Backend fix required before Stage 7; gate had been red since `63814ac`.
4. **JWT Claim:** `tenant_id` claim present; resolution stays header-based per ADR-0018 point 6. Stage 6/7: verify nothing reads the claim for authorization decisions.
5. **Release version for breaking contract:** `1.1.0` or `2.0.0`? Decision needed at release time (see PRD §6).
6. **On-premise default:** Confirm single `org-default` tenant per on-premise install; cloud shares schema (ADR-0016 Alternative A).

## 3. Exact Next Action (Start Here in New Session)
1. Fix QA-GATE-001 first: `SecurityRejectionStatPersistenceAdapter.increment()` uses `RejectionStatRow` only as an exists-vs-not carrier and never reads `getRejectionCount()`/`getLastSeenAt()`; both getter bodies are dead code and fail the JaCoCo per-CLASS LINE 0.80 gate (0.66). Backend: simplify the row mapper (e.g., map to a scalar/existence record without getters) or justify and request a gate exclusion. Then `mvn clean verify` must be green before Stage 7.
2. BUG-TENANT-001/004: both FIXED and QA-verified 2026-11-14 (see `06-TEST-PLAN.md` §10).
3. Stage 7 release prep: version bump decision (open question 5), CHANGELOG, deployment checklist (V10 rollback path); ensure gate green in CI first.

## 4. Decision & Switch Log
- [2026-11-14 | Stage 6] QA retest: BUG-TENANT-001/004 FIXED and verified (see 06-TEST-PLAN §10); missing-header unification to 401 re-verified; 44 tests added (257 -> 301); `mvn clean verify` red ONLY on JaCoCo per-class gate, QA-GATE-001 (RejectionStatRow dead getters), escalated to backend; gate had been red since 63814ac. — QA evidence, not waiver.
- [2026-11-14 | Stage 3 doc sync] Boss ruling (BUG-TENANT-001): reject 401 — unknown organization is 401 on all paths; missing/unknown org unified to 401 on all tenant-aware paths (Boss: "disatukan saja 401"); error body carries missing-vs-unknown cause. `03-ARCHITECTURE.md`, `ADR-0016`, `ADR-0018` rationale, API-SPEC, TenantHeaderFilter, and tests synced. — Reason: Boss ruling, 2026-11-14.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 migration, required header on login/refresh (401), refresh anchored to stored token tenant, BUG-TENANT-001 (unknown org -> 401 everywhere) and BUG-TENANT-004 (query predicate) fixed; login lookup was leaking across tenants pre-fix. CORS preflight allows X-Organization-ID. — Reason: Boss decision Option (a), 2026-11-14.
- [2026-11-14 | Stage 6 -> 4 -> 6] Fixed BUG-TENANT-002 (`requestURI` filter under MockMvc) and BUG-TENANT-003 (`user.getTenantId()` session attribution) in commit `8f125e8` — Reason: QA retest failures.
- [2026-11-14 | Stage 1] PRD updated for multi-tenancy + structured logging (check-id/check-bahasa clean); Boss approved Option (a) credit: marketing on-premise + cloud. — Reason: Boss decision, 2026-11-14.
- [2026-11-14 | Stage 3] ADR-0018 created: tenant-scoped identity, required `X-Organization-ID` on `/v1/auth/login` + `/v1/auth/refresh` (401 on unknown/missing org per ADR-0016 verification). — Reason: Boss decision, Option (a).
