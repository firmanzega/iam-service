# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7 - PRE-GO-LIVE VERIFICATION PASS (repo-side); NOT deployed; CI not re-verified (gh CLI absent)
- **Next Active Stage:** Stage 7 - Go-live execution (Boss deploys + prod checklist items)
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-14 19:38

## 1. Key Decisions & Locked Artifacts
- **ADR-0016/0018 (multi-tenancy):** shared schema, header `X-Organization-ID` wajib di seluruh endpoint tenant-aware (missing/unknown -> 401), identitas tenant-scoped `UNIQUE(tenant_id, username|email)` (V10), refresh di-anchor ke tenant token tersimpan. Committed `638c1d3`/`f88f6fa`/`d16d692`.
- **ADR-0017 (structured logging)** + ADR-0018 point 6 (JWT claim carried, header-based resolution).
- **Breaking contract folded into v1.0.0** (Boss Option B, 2026-11-14); CHANGELOG [1.0.0] re-scoped honestly; tag pushed on `577187f`; CI #29/#30 green, restore drill #14/#15 green.
- **Verify gate:** `mvn clean verify` BUILD SUCCESS (305/305 2026-11-14); repo debt historically red since `63814ac`, now green.
- **Boss rulings:** unknown org -> 401 (BUG-TENANT-001/004 fixed); missing/unknown header -> 401 everywhere ("disatukan saja 401"); Option A (V11) for rejection-stats; Option B (fold breaking into 1.0.0); on-premise = org-default single tenant.

## 2. Open Items / Residual Debt (B+ baseline)
| Debt | Owner | Fix |
|------|-------|-----|
| `04-API-SPEC.yaml` only documents `/auth/*` | BA/architect | document users/roles/audit |
| `WebMapper.tenantId` dead code (0 callers) | backend | delete + retest |
| Dependabot PRs #1-#4 CI red (pre-fix branches) | dependabot/merge | merge after triage |
| Sentinel tests not all mutation-proven | QA | discipline note |
| No live deploy/rollback executed | Boss/ops | go-live |
| Path to A- (see `07-DEPLOYMENT.md` §12.4): API-SPEC + dead code + dependabot + go-live §6 + re-audit | team | A- |
| cspell red repo-wide (24,558 issues / 199 files; Indonesian docs not in ignorePaths) | tooling/Boss | add ignorePaths or id locale; no mass-fix en docs |

## 3. Exact Next Action (Start Here in New Session)
1. Boss executes go-live per `07-DEPLOYMENT.md` §6 (prod-only items deliberately unchecked; release SHA `577187f` + tag `v1.0.0` fully green). Repo-side pre-go-live verification done 2026-11-14 19:28: tag OK, secret scan clean, prod config OK (see log).
2. After deploy: smoke `/actuator/health` + login; tick prod checklist; close Stage 7.

## 4. Decision & Switch Log
- [2026-11-14 19:28 | Stage 7] Pre-go-live verification: tag v1.0.0 = `577187f` confirmed; only docs(worklog) commits after tag; secret scan clean (only restore-drill throwaway pw; .env gitignored; .env.example placeholders); Dockerfile USER 10001:10001; prod ddl-auto=validate + springdoc disabled confirmed; 07-DEPLOYMENT.md cspell 0 issues. gh CLI ABSENT -> CI #29/#30 NOT re-verified (prior record stands); `mvn clean verify` NOT re-run (0 source changes since 305/305 green). cspell repo-wide red (Indonesian docs) -> debt row. — Reason: Stage 7 gate before Boss go-live.
- [2026-11-14 19:37 | Stage 7] Release grading rubric added (`07-DEPLOYMENT.md` §12): project-internal, grade = lowest unmet mandatory criterion, evidence required, §9 accepted residuals block A/A+, regression drops grade. Current grade stays B+; A+ needs Boss decisions (infra, privacy, re-open residuals). — Reason: Boss approved rubric-first (point 1).
- [2026-11-14 | Stage 7] v1.0.0 TAGGED on `577187f` (annotated, pushed). Verified: CI #29(f156757)/#30(577187f) SUCCESS; restore drill #14/#15 SUCCESS; checklist §6 honestly scoped (repo items [x], prod items pending). Deploy NOT executed. — Reason: Stage 7 close-out.
- [2026-11-14 | Stage 7] TEK-DEBT B+ recorded honestly (A- was premature: verify red since `63814ac`; drill red since creation; each close found deeper debt). Path to A- in section 2. — Reason: Boss asked.
- [2026-11-14 | Stage 7] RESTORE DRILL GREEN #13/#14/#15 (seed heredoc w/o docker exec -i; string_agg order) after CI #16 order-dependent UserServiceTest fix (masked by AuthServiceTest leak; `4d7a344`); CI #20/#23 green. — Reason: Stage 7 gate.
- [2026-11-14 | Stage 4] ADR-0018 implemented: V10 tenant-scoped uniques (Option A), login/refresh require X-Organization-ID (401), refresh anchored to stored token, BUG-TENANT-001/004 + SEC-STATS-001 + SEC-USERS-001 IDOR closed (V11 upsert, tenant-scoped repos). — Reason: Boss decisions, 2026-11-14.
