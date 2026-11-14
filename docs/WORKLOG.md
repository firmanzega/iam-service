# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7 - A- items 1/2/3/4 DONE + BUG-TENANT-005 FIXED; dependabot zero-open; go-live NOT executed (Boss gate: §6)
- **Next Active Stage:** Stage 7 - Go-live execution (Boss deploys v1.0.0 + prod checklist; then re-audit vs §12.4)
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-14 20:28

## 1. Key Decisions & Locked Artifacts
- **ADR-0016/0018 (multi-tenancy):** shared schema, header `X-Organization-ID` wajib di seluruh endpoint tenant-aware (missing/unknown -> 401), identitas tenant-scoped `UNIQUE(tenant_id, username|email)` (V10), refresh di-anchor ke tenant token tersimpan. Committed `638c1d3`/`f88f6fa`/`d16d692`.
- **ADR-0017 (structured logging)** + ADR-0018 point 6 (JWT claim carried, header-based resolution).
- **Breaking contract folded into v1.0.0** (Boss Option B, 2026-11-14); CHANGELOG [1.0.0] re-scoped honestly; tag pushed on `577187f`; CI #29/#30 green, restore drill #14/#15 green.
- **Verify gate:** `mvn clean verify` BUILD SUCCESS (300/300 2026-11-14, was 305; 5 dead-code tests removed); repo debt historically red since `63814ac`, now green. cspell repo-wide gate GREEN 2026-11-14 (225 files, 0 issues; bilingual dictionary import).
- **Boss rulings:** unknown org -> 401 (BUG-TENANT-001/004 fixed); missing/unknown header -> 401 everywhere ("disatukan saja 401"); Option A (V11) for rejection-stats; Option B (fold breaking into 1.0.0); on-premise = org-default single tenant.

## 2. Open Items / Residual Debt (A- path, `07-DEPLOYMENT.md` §12.4)
| Debt | Owner | Fix |
|------|-------|-----|
| Dependabot zero open: PRs #1-#4 rebased (bump-only), CI all green (Build/Drill/dependency-review after graph enabled), merged 2026-11-14 (`46281c6`/`6c555b2`/`786a37b`/`3475d78` -> `2e599f3`), verify green on merged main (303/303) | DONE | A- item 3 closed |
| Sentinel tests not all mutation-proven | QA | discipline note |
| No live deploy/rollback executed | Boss/ops | go-live |
| Path to A-: API-SPEC DONE, dead code DONE, cspell DONE, BUG-TENANT-005 DONE, dependabot zero-open DONE; go-live §6 + deploy/rollback + post-deploy re-audit remain | team | A- |

## 3. Exact Next Action (Start Here in New Session)
1. A- IN PROGRESS 2026-11-14: item 1 API-SPEC DONE; item 2 dead code DONE (300/300); item 3 dependabot rebased+pushed (Build+Drill green; dependency-review infra-red, awaiting Boss ruling); item 4 cspell repo-wide GREEN; BUG-TENANT-005 FIXED (`c18f14a`, 303/303) — `/v1/audit-logs` now 401 on missing X-Organization-ID; main pushed (`c18f14a`). Remaining: dependabot merge ruling, go-live §6, re-audit (§12.4), then A- claim valid.
2. Boss executes go-live per `07-DEPLOYMENT.md` §6 (prod items unchecked; release SHA `577187f`). After deploy: smoke `/actuator/health` + login; tick §6; re-audit vs §12.4; close Stage 7.

## 4. Decision & Switch Log
- [2026-11-14 19:43 | Stage 7/A-] sec -> solution-architect: API-SPEC completed (all /v1: users, roles, audit) in `23e7052`. Gap found & logged: BUG-TENANT-005 (missing header on /v1/audit-logs). NOT fixed (role lock + Boss decision). — Reason: A- item 1.
- [2026-11-14 19:52 | Stage 4->7/A-] sec -> backend -> sec: `WebMapper.tenantId` + 5 dead-code tests deleted (`d28f832`); `mvn clean verify` BUILD SUCCESS 300/300 (exit 0; earlier exit=1 was the stderr-wrap artifact — re-verified). — Reason: A- item 2.
- [2026-11-14 20:17 | Stage 7/A-] Re-audit v2 (Boss asked): verify 303/303, cspell 0, Dockerfile/CI/migrations/prod-config/scripts untouched since tag; API-SPEC = 16 paths/20 ops (mechanical match with controllers); secret scan clean; grade honestly stays B+. — Reason: Boss asked for re-audit.
- [2026-11-14 20:28 | Stage 7/A-] Dependabot #1-#4 MERGED: Boss enabled Dependency graph (Option A); 4 PRs rebased onto origin/main (fresh SHAs) -> CI all green (Build+Drill+dependency-review SUCCESS) -> merged sequentially (1 ff + 3 ort merges) -> `2e599f3`; verify on merged main BUILD SUCCESS 303/303 (incl. JaCoCo 0.8.16); PRs closed/merged verified via API. — Reason: A- item 3 closed.
- [2026-11-14 20:11 | Stage 7/A-] sec -> senior-backend-developer: BUG-TENANT-005 FIXED (`c18f14a`): `/v1/audit-logs[/verify]` added to TENANT_REQUIRED_PATHS (missing header -> 401); silent org-default fallback in `AuditService.currentTenantId()` now logs WARN; +3 regression tests; 303/303 BUILD SUCCESS; main pushed. — Reason: Boss "Gas fixing".
- [2026-11-14 19:55 | Stage 7/A-] Dependabot #1-#4 rebased onto origin/main (bump-only diffs) & force-pushed; target versions/artifacts verified (tags/Central). Superseded by 20:28 merge. — Reason: A- item 3.
- [2026-11-14 19:57 | Stage 7/A-] cspell repo-wide GREEN: 225 files, 0 issues. Root cause: repo bilingual (en + id prose); en-only config flagged 24,536 issues. No npm id dictionary exists; created `cspell-id.json` (2,666 tokens, suspect-misspelling scan clean) imported by `cspell.json`. Whitelists Indonesian vocab; new typos still caught. — Reason: A- item 4.
- [2026-11-14 19:37 | Stage 7] Release grading rubric added (`07-DEPLOYMENT.md` §12): grade = lowest unmet mandatory criterion; §9 residuals block A/A+; regression drops grade; grade stays B+. Restore drill green #13-#15 after CI #16 fix (`4d7a344`); v1.0.0 tagged `577187f`. — Reason: Boss rubric-first + Stage 7 gate/close-out.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 tenant-scoped uniques (Option A), login/refresh require X-Organization-ID (401), refresh anchored to stored token, BUG-TENANT-001/004 + SEC-STATS-001 + SEC-USERS-001 IDOR closed (V11 upsert). — Reason: Boss decisions, 2026-11-14.
