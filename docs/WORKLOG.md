# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 4 fix of Stage 6 escalation (QA-GATE-001 closed; verify BUILD SUCCESS 299/299)
- **Next Active Stage:** Stage 7 - Release Prep (blocked only on SEC-STATS-001 ruling + version bump 1.1.0 vs 2.0.0)
- **Active Skill to Load:** `solution-architect` (SEC-STATS-001 ruling) or `senior-security-engineer` (Stage 7)
- **Last Updated:** 2026-11-14 15:50

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
4. **QA-GATE-001:** FIXED 2026-11-14 — `mvn clean verify` BUILD SUCCESS (299/299, JaCoCo per-class met, Checkstyle 0, SpotBugs 0). Backend removed dead `RejectionStatRow`; `LogMessage.category` removed too (ADR-0017: LogMessage is Generals-only). Gate history: red since `63814ac`.
5. **SEC-STATS-001 (new, Medium, escalated):** `security_rejection_stats` still has V8's GLOBAL `uq_security_rejection_stats UNIQUE (window_start, http_method, reason)` without `tenant_id`; a second tenant's same-minute rejection violates it and the recorder swallows it (`persistRejectionStat`) → that tenant's 401 aggregate silently lost. Fix = V11 migration (composite tenant-scoped unique) — schema change to a core table, needs Boss/solution-architect approval, NOT done. Related: `08-CODEBASE-MAP.md` claims adapter uses `ON CONFLICT upsert`; implementation is SELECT-then-INSERT/UPDATE (docs drift, minor).
6. **JWT Claim:** `tenant_id` claim present; resolution stays header-based per ADR-0018 point 6. Stage 6/7: verify nothing reads the claim for authorization decisions.
7. **Release version for breaking contract:** `1.1.0` or `2.0.0`? Decision needed at release time (see PRD §6).
8. **On-premise default:** Confirm single `org-default` tenant per on-premise install; cloud shares schema (ADR-0016 Alternative A).

## 3. Exact Next Action (Start Here in New Session)
1. Boss ruling needed on SEC-STATS-001 (V11 migration for tenant-scoped unique on `security_rejection_stats`, or accepted as known limitation for on-premise-only). If approved, that is a solution-architect + backend task.
2. Stage 7 release prep: version bump decision (open question 7), CHANGELOG, deployment checklist (V10 rollback path); `mvn clean verify` is green as of 2026-11-14 (299/299).
3. BUG-TENANT-001/004: both FIXED and QA-verified 2026-11-14 (see `06-TEST-PLAN.md` §10).

## 4. Decision & Switch Log
- [2026-11-14 | Stage 6 -> 4] Backend verified and closed QA-GATE-001: dead `RejectionStatRow` removed (existence-only `SELECT 1` -> Integer), `LogMessage.category` removed per ADR-0017, Checkstyle 14 -> 0 (13 pre-existing violations from 63814ac finally visible once JaCoCo stopped failing first), SpotBugs Medium clean (LoggingServiceImpl excluded via sanctioned `config/spotbugs/exclude.xml` Group-1 pattern + LogMessage defensive copies), `mvn clean verify` BUILD SUCCESS 299/299. NEW finding SEC-STATS-001 (V8 global unique on security_rejection_stats drops non-default tenants' 401 aggregates) escalated to Boss — schema change to core table, not done unilaterally. — Reason: Stage 6 retest required verify green before release.
- [2026-11-14 | Stage 6] QA retest: BUG-TENANT-001/004 FIXED and verified (see 06-TEST-PLAN §10); missing-header unification to 401 re-verified; 44 tests added (257 -> 301); `mvn clean verify` red ONLY on JaCoCo per-class gate, QA-GATE-001 (RejectionStatRow dead getters), escalated to backend; gate had been red since 63814ac. Honesty note: sentinel tests proven failable only via pre-fix/post-fix history, NOT via formal per-test mutation runs. — QA evidence, not waiver.
- [2026-11-14 | Stage 3 doc sync] Boss ruling (BUG-TENANT-001): reject 401 — unknown organization is 401 on all paths; missing/unknown org unified to 401 on all tenant-aware paths (Boss: "disatukan saja 401"); error body carries missing-vs-unknown cause. `03-ARCHITECTURE.md`, `ADR-0016`, `ADR-0018` rationale, API-SPEC, TenantHeaderFilter, and tests synced. — Reason: Boss ruling, 2026-11-14.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 migration, required header on login/refresh (401), refresh anchored to stored token tenant, BUG-TENANT-001 (unknown org -> 401 everywhere) and BUG-TENANT-004 (query predicate) fixed; login lookup was leaking across tenants pre-fix. CORS preflight allows X-Organization-ID. — Reason: Boss decision Option (a), 2026-11-14.
- [2026-11-14 | Stage 6 -> 4 -> 6] Fixed BUG-TENANT-002 (`requestURI` filter under MockMvc) and BUG-TENANT-003 (`user.getTenantId()` session attribution) in commit `8f125e8` — Reason: QA retest failures.
- [2026-11-14 | Stage 1] PRD updated for multi-tenancy + structured logging (check-id/check-bahasa clean); Boss approved Option (a) credit: marketing on-premise + cloud. — Reason: Boss decision, 2026-11-14.
- [2026-11-14 | Stage 3] ADR-0018 created: tenant-scoped identity, required `X-Organization-ID` on `/v1/auth/login` + `/v1/auth/refresh` (401 on unknown/missing org per ADR-0016 verification). — Reason: Boss decision, Option (a).
