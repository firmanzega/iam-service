# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7 - A- items 1/2/4 DONE; item 3 dependabot rebased+pushed (awaiting CI+merge); go-live NOT executed
- **Next Active Stage:** Stage 7 - Go-live execution (Boss deploys + prod checklist; then re-audit vs §12.4)
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-14 19:58

## 1. Key Decisions & Locked Artifacts
- **ADR-0016/0018 (multi-tenancy):** shared schema, header `X-Organization-ID` wajib di seluruh endpoint tenant-aware (missing/unknown -> 401), identitas tenant-scoped `UNIQUE(tenant_id, username|email)` (V10), refresh di-anchor ke tenant token tersimpan. Committed `638c1d3`/`f88f6fa`/`d16d692`.
- **ADR-0017 (structured logging)** + ADR-0018 point 6 (JWT claim carried, header-based resolution).
- **Breaking contract folded into v1.0.0** (Boss Option B, 2026-11-14); CHANGELOG [1.0.0] re-scoped honestly; tag pushed on `577187f`; CI #29/#30 green, restore drill #14/#15 green.
- **Verify gate:** `mvn clean verify` BUILD SUCCESS (300/300 2026-11-14, was 305; 5 dead-code tests removed); repo debt historically red since `63814ac`, now green. cspell repo-wide gate GREEN 2026-11-14 (225 files, 0 issues; bilingual dictionary import).
- **Boss rulings:** unknown org -> 401 (BUG-TENANT-001/004 fixed); missing/unknown header -> 401 everywhere ("disatukan saja 401"); Option A (V11) for rejection-stats; Option B (fold breaking into 1.0.0); on-premise = org-default single tenant.

## 2. Open Items / Residual Debt (A- path, `07-DEPLOYMENT.md` §12.4)
| Debt | Owner | Fix |
|------|-------|-----|
| Missing X-Organization-ID NOT rejected on `/v1/audit-logs` (filter allows it; query scoped by null tenant -> 200 empty page; `currentTenantId()` silently falls back to org-default for 403 audits) | backend/Boss | BUG-TENANT-005 candidate; add path to TENANT_REQUIRED_PATHS per ADR-0018 + retest; needs Boss call |
| Dependabot PRs #1-#4: rebased onto origin/main (bump-only) & pushed 2026-11-14 (`15d282b`/`ed9a52b`/`32587e0`/`7d1ed14`); awaiting CI + Boss merge | Boss/sec | merge after green CI; `gh` CLI absent -> CI not re-verified here |
| Sentinel tests not all mutation-proven | QA | discipline note |
| No live deploy/rollback executed | Boss/ops | go-live |
| Local main AHEAD of origin/main (unpushed docs+rubric+API-SPEC+dead code+cspell; see `git log origin/main..main`) | Boss | push decision needed |
| Path to A-: API-SPEC DONE, dead code DONE, cspell DONE; dependabot merge + go-live §6 + re-audit remain | team | A- |

## 3. Exact Next Action (Start Here in New Session)
1. A- IN PROGRESS 2026-11-14 (local main unpushed, see `git log origin/main..main`): item 1 API-SPEC DONE (`23e7052`); item 2 dead code `WebMapper.tenantId` DONE (`d28f832`, 300/300); item 3 dependabot rebased onto origin/main (bump-only) & pushed, CI re-running, merge needs Boss + green CI; item 4 cspell repo-wide GREEN (`24dfe22`). Remaining: dependabot merge, go-live §6, re-audit (§12.4), then A- claim valid.
2. Boss executes go-live per `07-DEPLOYMENT.md` §6 (prod items unchecked; release SHA `577187f`). After deploy: smoke `/actuator/health` + login; tick §6; re-audit vs §12.4; close Stage 7. Push local main when Boss decides.

## 4. Decision & Switch Log
- [2026-11-14 19:43 | Stage 7/A-] sec -> solution-architect: API-SPEC completed (all /v1: users, roles, audit) in `23e7052`. Gap found & logged: BUG-TENANT-005 (missing header on /v1/audit-logs). NOT fixed (role lock + Boss decision). — Reason: A- item 1.
- [2026-11-14 19:52 | Stage 4->7/A-] sec -> backend -> sec: `WebMapper.tenantId` + 5 dead-code tests deleted (`d28f832`); `mvn clean verify` BUILD SUCCESS 300/300 (exit 0; earlier exit=1 was the stderr-wrap artifact — re-verified). — Reason: A- item 2.
- [2026-11-14 19:55 | Stage 7/A-] Dependabot #1-#4 rebased onto origin/main (bump-only diffs) & force-pushed (`15d282b` checkout-7, `ed9a52b` setup-java-6, `32587e0` upload-artifact-7, `7d1ed14` jacoco-0.8.16); all 4 target versions + existence verified (tags/releases/Central). CI re-running; merge NOT done (needs Boss + green CI). Local main unpushed (see `git log origin/main..main`). — Reason: A- item 3.
- [2026-11-14 19:57 | Stage 7/A-] cspell repo-wide GREEN: 225 files, 0 issues. Root cause: repo bilingual (en + id prose); en-only config flagged 24,536 issues. No npm id dictionary exists; created `cspell-id.json` (2,666 tokens, suspect-misspelling scan clean) imported by `cspell.json`. Whitelists Indonesian vocab; new typos still caught. — Reason: A- item 4.
- [2026-11-14 19:37 | Stage 7] Release grading rubric added (`07-DEPLOYMENT.md` §12): project-internal; grade = lowest unmet mandatory criterion; evidence required; §9 residuals block A/A+; regression drops grade; current grade stays B+. — Reason: Boss approved rubric-first.
- [2026-11-14 | Stage 7] RESTORE DRILL GREEN #13/#14/#15 after CI #16 order-dependent UserServiceTest fix (`4d7a344`); CI #20/#23 green; v1.0.0 TAGGED on `577187f`; restore drill #14/#15 green; §6 honestly scoped. — Reason: Stage 7 gate/close-out.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 tenant-scoped uniques (Option A), login/refresh require X-Organization-ID (401), refresh anchored to stored token, BUG-TENANT-001/004 + SEC-STATS-001 + SEC-USERS-001 IDOR closed (V11 upsert). — Reason: Boss decisions, 2026-11-14.
