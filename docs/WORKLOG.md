# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 6 - QA Re-verification (SEC-STATS-001 closed: cross-tenant 401 aggregate verified, test proven failable; 299/299 green, BUILD SUCCESS)
- **Next Active Stage:** Stage 7 - Release Prep (version bump 1.1.0 vs 2.0.0; CHANGELOG; deployment checklist)
- **Active Skill to Load:** `senior-security-engineer` (Stage 7)
- **Last Updated:** 2026-11-14 16:50

## 1. Key Decisions & Locked Artifacts
- **Stage 7 (Security & Release):** `07-DEPLOYMENT.md` + `CHANGELOG.md` v1.0.0 done (committed); Package A GO pending SEC-STATS-001 + BUG-TENANT-001/004 — all three FIXED and QA-verified 2026-11-14; verify green 299/299.
- **Stage 3 (Architecture):** ADR-0016 (`tenant_id` / `X-Organization-ID`) + ADR-0017 in `638c1d3`; ADR-0018 (tenant-scoped identity) in `f88f6fa`; Scenario D deferred.
- **Stage 4 & 6 (Implementation & QA):** `V9` tenant migration + tenant-aware backend (`63814ac`); BUG-TENANT-002/003 fixed (`8f125e8`); ADR-0018 implemented (`d16d692`): `V10` composite unique, required header on login/refresh (401), stored-token-tenant refresh (ADR-0018 pt. 6), BUG-TENANT-001 fixed (unknown org -> 401 everywhere, unified with missing header via Boss "disatukan saja 401") + BUG-TENANT-004 fixed (login lookup was leaking across tenants); 401-missing contract synced everywhere (`e60973f`, `f57ca24`); 257/257 green.
- **Stage 6 -> 4 (verify gate):** `mvn clean verify` had been red since `63814ac` (JaCoCo per-class -> Checkstyle 14 -> SpotBugs 3). Dead `RejectionStatRow` removed (exists-only `SELECT 1` -> `Integer`); `LogMessage.category` removed (ADR-0017); Checkstyle 0, SpotBugs 0; 299/299 green, commit `56fa3fc`.

## 2. Open Questions / Blockers for User
1. **RESOLVED (2026-11-14):** missing `X-Organization-ID` is 401 everywhere including sessions/logout (Boss: "disatukan saja 401"); unknown org is 401 everywhere; cause carried by error body.
2. **BUG-TENANT-001:** FIXED + QA-verified 2026-11-14 (401 on every path).
3. **BUG-TENANT-004:** FIXED + QA-verified 2026-11-14 (V10 composite unique; two-tenant same-identity login passes).
4. **QA-GATE-001:** FIXED 2026-11-14 — verify BUILD SUCCESS (299/299, JaCoCo per-class met, Checkstyle 0, SpotBugs 0). Gate red since `63814ac`.
5. **SEC-STATS-001 — FIXED + QA-verified 2026-11-14 (Option A, Boss-approved):** `V11` composite tenant-scoped unique on `security_rejection_stats`; `increment()` = single-statement upsert. QA re-verified: `TenantIsolationIntegrationTest.unauthorized_aggregatesPerTenant_sameMinuteSameMethodAndReason` (both tenants increment same minute/method/reason); test proven failable by temporarily reverting the upsert (`expected <2> but was <1>`), then restored. Closed in `06-TEST-PLAN.md` §6/§10.3.
6. **JWT Claim:** `tenant_id` claim present; resolution stays header-based per ADR-0018 point 6. Stage 6/7: verify nothing reads the claim for authorization decisions.
7. **Release version for breaking contract:** `1.1.0` or `2.0.0`? Decision needed at release time (see PRD §6).
8. **On-premise default:** Confirm single `org-default` tenant per on-premise install; cloud shares schema (ADR-0016 Alternative A).

## 3. Exact Next Action (Start Here in New Session)
1. Stage 7 release prep: version bump decision (open question 7), CHANGELOG, deployment checklist (V10/V11 rollback path); `mvn clean verify` green as of 2026-11-14 (299/299).
2. BUG-TENANT-001/004 and SEC-STATS-001: all FIXED and QA-verified 2026-11-14 (see `06-TEST-PLAN.md` §6/§10).

## 4. Decision & Switch Log
- [2026-11-14 | Stage 6] SEC-STATS-001 QA re-verified and CLOSED: cross-tenant same-minute 401 aggregate test added (`TenantIsolationIntegrationTest.unauthorized_aggregatesPerTenant_sameMinuteSameMethodAndReason`); fixture gained `security_rejection_stats` cleanup before tenant delete (FK enforced); test proven failable by temporarily reverting the upsert to a plain INSERT (`expected <2> but was <1>`), restored via git checkout. `mvn clean verify` BUILD SUCCESS 299/299. NOTE (role-lock honesty): the mutation step edited production code under the QA role; reverted exactly afterwards — future mutations must switch to the backend role explicitly. — Reason: 4.4 proof of failure.
- [2026-11-14 | Stage 4] SEC-STATS-001 fixed (Option A, Boss ruling 2026-11-14): V11 drops global `uq_security_rejection_stats`, adds composite `uq_security_rejection_stats_tenant UNIQUE (tenant_id, window_start, http_method, reason)`; increment() is one `INSERT ... ON CONFLICT DO UPDATE`; TOCTOU race removed; 298/298 green, Checkstyle 0. QA re-verification pending at the time. — Reason: Boss decision, Option A.
- [2026-11-14 | Stage 3] Architect review of SEC-STATS-001: recommended Option A; rejected drop-constraint; on-premise-only fallback documented. Boss ruling 2026-11-14: Option A. — Reason: ADR-0016 tenant purity.
- [2026-11-14 | Stage 6 -> 4] Backend verified and closed QA-GATE-001: dead `RejectionStatRow` removed (existence-only `SELECT 1` -> Integer), `LogMessage.category` removed per ADR-0017, Checkstyle 14 -> 0 (13 pre-existing violations from 63814ac finally visible once JaCoCo stopped failing first), SpotBugs Medium clean (LoggingServiceImpl excluded via sanctioned `config/spotbugs/exclude.xml` Group-1 pattern + LogMessage defensive copies), `mvn clean verify` BUILD SUCCESS 299/299. NEW finding SEC-STATS-001 (V8 global unique on security_rejection_stats drops non-default tenants' 401 aggregates) escalated to Boss — schema change to core table, not done unilaterally. — Reason: Stage 6 retest required verify green before release.
- [2026-11-14 | Stage 6] QA retest: BUG-TENANT-001/004 FIXED and verified (see 06-TEST-PLAN §10); missing-header unification to 401 re-verified; 44 tests added (257 -> 301); `mvn clean verify` red ONLY on JaCoCo per-class gate, QA-GATE-001 (RejectionStatRow dead getters), escalated to backend; gate had been red since 63814ac. Honesty note: sentinel tests proven failable only via pre-fix/post-fix history, NOT via formal per-test mutation runs. — QA evidence, not waiver.
- [2026-11-14 | Stage 3 doc sync] Boss ruling (BUG-TENANT-001): reject 401 — unknown organization is 401 on all paths; missing/unknown org unified to 401 on all tenant-aware paths (Boss: "disatukan saja 401"); error body carries missing-vs-unknown cause. `03-ARCHITECTURE.md`, `ADR-0016`, `ADR-0018` rationale, API-SPEC, TenantHeaderFilter, and tests synced. — Reason: Boss ruling, 2026-11-14.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 migration, required header on login/refresh (401), refresh anchored to stored token tenant, BUG-TENANT-001 (unknown org -> 401 everywhere) and BUG-TENANT-004 (query predicate) fixed; login lookup was leaking across tenants pre-fix. CORS preflight allows X-Organization-ID. — Reason: Boss decision Option (a), 2026-11-14.
- [2026-11-14 | Stage 6 -> 4 -> 6] Fixed BUG-TENANT-002 (`requestURI` filter under MockMvc) and BUG-TENANT-003 (`user.getTenantId()` session attribution) in commit `8f125e8` — Reason: QA retest failures.
- [2026-11-14 | Stage 1] PRD updated for multi-tenancy + structured logging (check-id/check-bahasa clean); Boss approved Option (a) credit: marketing on-premise + cloud. — Reason: Boss decision, 2026-11-14.
- [2026-11-14 | Stage 3] ADR-0018 created: tenant-scoped identity, required `X-Organization-ID` on `/v1/auth/login` + `/v1/auth/refresh` (401 on unknown/missing org per ADR-0016 verification). — Reason: Boss decision, Option (a).
