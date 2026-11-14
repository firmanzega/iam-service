# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7 - GO-LIVE EXECUTED & VERIFIED (2026-11-14/15): v1.0.1 deployed on `192.168.18.250:9090` (LAN-only); health `UP`; admin created & password rotated; backup verified (restore drill passed); clone/artifacts cleaned
- **Next Active Stage:** Stage 7 - Post-deploy close-out (`07-DEPLOYMENT.md` §6 prod items + re-audit vs §12.4; then A- may be claimable)
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-15 02:07

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
| ~~No live deploy/rollback executed~~ DONE 2026-11-14/15: v1.0.1 `4ebec1e` deployed to LAN host `192.168.18.250:9090`; health `UP` from host + mobile; `/actuator/prometheus` -> 404 from outside (internal-only); backup verified via restore drill; passwords rotated | ops/Boss | post-deploy re-audit vs §12.4 |
| Sentinel tests not all mutation-proven | QA | discipline note |
| Path to A-: API-SPEC DONE, dead code DONE, cspell DONE, BUG-TENANT-005 DONE, dependabot zero-open DONE, go-live DONE; remaining: §6 prod checklist items + post-deploy re-audit (§12.4) | team | A- |

## 3. Exact Next Action (Start Here in New Session)
1. Stage 7 close-out: tick `07-DEPLOYMENT.md` §6 prod items (all prod-only items remain unchecked until Boss confirms on host), then re-audit vs §12.4 (items 5-7) — if green, A- claimable.
2. Rollback exercise against the live host (or accept documented risk); post-deploy re-audit; update §12.7 honest position; then Stage 7 can close.
3. Housekeeping done 2026-11-15: `scripts/` copied to `E:\prod\iam-deploy\scripts\`; `backup-2026-11-15.dump` (SHA-256 `9E23FDC4...733E`) restore-tested OK; git clone `iam-service-1.0.1` deleted; `secrets/.env` ACL-locked; compose moved out of clone; `docker-compose.prod.yml` is at `E:\prod\iam-deploy\app\docker-compose.prod.yml` with absolute `env_file:`.

## 4. Decision & Switch Log
- [2026-11-14/15 | Stage 7] GO-LIVE EXECUTED: v1.0.1 (`4ebec1e`) deployed on `192.168.18.250:9090` (LAN-only); health `UP` (host+mobile); `/actuator/prometheus` -> 404; admin created via SQL + password rotated; superuser password rotated; `.env` cleaned (`IAM_DEV_ADMIN_*` removed, ACL `icacls`); backup `backup-2026-11-15.dump` (SHA `9E23FDC4...733E`) restore-verified OK; clone deleted; `scripts/` at `E:\prod\iam-deploy\scripts\`; compose at `E:\prod\iam-deploy\app\docker-compose.prod.yml` (absolute `env_file:`); `CHANGE-LOG-GO-LIVE.md` written. — Reason: Boss "malam ini".
- [2026-11-14 19:43 | Stage 7/A-] sec -> solution-architect: API-SPEC completed (all /v1: users, roles, audit) in `23e7052`. Gap found & logged: BUG-TENANT-005 (missing header on /v1/audit-logs). NOT fixed (role lock + Boss decision). — Reason: A- item 1.
- [2026-11-14 19:52 | Stage 4->7/A-] sec -> backend -> sec: `WebMapper.tenantId` + 5 dead-code tests deleted (`d28f832`); `mvn clean verify` BUILD SUCCESS 300/300 (exit 0; earlier exit=1 was the stderr-wrap artifact — re-verified). — Reason: A- item 2.
- [2026-11-14 20:17 | Stage 7/A-] Re-audit v2 (Boss asked): verify 303/303, cspell 0, Dockerfile/CI/migrations/prod-config/scripts untouched since tag; API-SPEC = 16 paths/20 ops (mechanical match with controllers); secret scan clean; grade honestly stays B+. — Reason: Boss asked for re-audit.
- [2026-11-14 20:36 | Stage 7] v1.0.1 release prepared: CHANGELOG [1.0.1] added (BUG-TENANT-005 fix, CI bumps, API-SPEC completion, dead-code removal; non-breaking). CI on `ed35cb1` green (CI + restore drill, push events). Tag v1.0.1 on HEAD after this commit. — Reason: Boss approved v1.0.1.
- [2026-11-14 20:28 | Stage 7/A-] Dependabot #1-#4 MERGED: Boss enabled Dependency graph (Option A); CI all green; merged -> `2e599f3`; verify on merged main BUILD SUCCESS 303/303; PRs closed/merged verified via API. — Reason: A- item 3 closed.
- [2026-11-14 20:11 | Stage 7/A-] sec -> senior-backend-developer: BUG-TENANT-005 FIXED (`c18f14a`): `/v1/audit-logs[/verify]` added to TENANT_REQUIRED_PATHS (missing header -> 401); silent org-default fallback in `AuditService.currentTenantId()` now logs WARN; +3 regression tests; 303/303 BUILD SUCCESS; main pushed. — Reason: Boss "Gas fixing".
- [2026-11-14 19:57 | Stage 7/A-] cspell repo-wide GREEN: 225 files, 0 issues. Root cause: repo bilingual (en + id prose); en-only config flagged 24,536 issues. No npm id dictionary exists; created `cspell-id.json` (2,666 tokens, suspect-misspelling scan clean) imported by `cspell.json`. Whitelists Indonesian vocab; new typos still caught. — Reason: A- item 4.
- [2026-11-14 19:37 | Stage 7] Release grading rubric added (`07-DEPLOYMENT.md` §12): grade = lowest unmet mandatory criterion; §9 residuals block A/A+; regression drops grade; grade stays B+. Restore drill green #13-#15 after CI #16 fix (`4d7a344`); v1.0.0 tagged `577187f`. — Reason: Boss rubric-first + Stage 7 gate/close-out.
