# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7B pentest (2026-11-16, isolated throwaway env): 0 confirmed Medium+ findings; docs/10-PENTEST.md created; prod untouched
- **Next Active Stage:** Stage 7 close-out Boss attestation; soak v1.1.0 in prod; A/A+ blocked by accepted residuals (SEC-01 DB 5433 LAN, SEC-02 prometheus anon)
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-16 (cloud roadmap + OPS procedure)

## 1. Key Decisions & Locked Artifacts
- **v1.1.0 (`417e266`) deployed + rollback proven 2026-11-16.** CI+drill green; Flyway V12 auto-applied; OPS account `ops` created (Boss cred). HTTPS live (internal CA, `iam.osprey.service`); plain HTTP → 400. Grade **A-** (§12.4 item 7). DNS override + CA import to Windows Machine Root done 2026-11-16 (verify/sessions/CORS OK).
- **2026-11-16 cloud roadmap (ba+sa, documentation only, no code):** cloud = shared multi-tenant SaaS, still **exploration, no concrete tenant**. Locked: (1) one codebase, config-driven (on-prem+cloud, ADR-0018 artifact rule); (2) Phase 1 onboarding = **admin-provisioned by OPS team** (not one person, Tafsir A: OPS global per deployment, never per tenant; on-prem via DBA SQL); (3) first tenant ADMIN bootstrapped **via endpoint post-tenant-creation** — not manual SQL (ADR-0020, built only when trigger: concrete tenant/go decision); (4) roles stay **global** — NO per-tenant custom roles; tenant app permissions self-managed by tenant apps (ADR-0021); (5) self-registration = Phase 2, gated (≥1 real tenant/customer asks); (6) email absent → known limitation (password reset admin-only); (7) public cloud exposure **requires WAF** (plan: Cloudflare; final vendor at first real deployment); (8) CORS same-origin. Docs: `01-PRD.md` §1/§4/§7, `adr/0020`, `adr/0021`; check-id & check-bahasa PASS.
- **Roles locked:** `ADMIN`, `AUDITOR`, `USER`, `MANAGER` (tenant-assignable, tenant-scoped), `OPS` (global, deployment-operator only).
- **Uncommitted:** docs changes for cloud roadmap + earlier pending docs commits (Boss push decision).

## 2. Open Items / Next
| Item | Owner | Note |
|------|-------|------|
| Push docs: `01-PRD`, `adr/0020`, `adr/0021`, `WORKLOG` | Boss | git status; earlier pending commits too |
| Soak v1.1.0 days; no load test ever run | Boss | gates any A claim |
| A/A+ closure paths (SEC-01, SEC-02, CRL host, pen-test) | Boss | decisions, no date |
| OPS handover procedure → `07-DEPLOYMENT.md` §13 (§13.1 added 2026-11-16, sec) | Boss | DONE; PRD §7 item answered — needs BA to close checkbox next BA session |
| Cloud: build Phase 1 tooling (ADR-0020) only on concrete tenant/go | Boss | PRD §7 terbuka; WAF final at deployment |

## 3. Exact Next Action (Start Here in New Session)
1. Boss pushes docs commits (`git add docs/01-PRD.md docs/adr/0020-*.md docs/adr/0021-*.md docs/WORKLOG.md`).
2. Boss answers: add OPS handover procedure to `07-DEPLOYMENT.md` §13? (PRD §7).
3. Stage 7 close-out: Boss attestation; watch soak; re-audit only if sec needed.
4. Cloud stays documentation-only until a concrete tenant appears.

## 4. Decision & Switch Log
- [2026-11-16 OPS procedure] sec switch; `07-DEPLOYMENT.md` §13.1 added: personal accounts, four-eyes grant, immediate revoke triggers, quarterly review (default), logging rules; org-specific names deliberately left as Boss open items. PRD §7 "OPS handover → §13?" answered YES.
- [2026-11-16 cloud] ba↔sa switches for dual analysis; Boss approved execution of "pencatatan (1–4)". Decisions (b) custom roles: understood B1 permission-catalog design, then ruled EXISTING model kept (tenant apps self-manage). Trigger-based build, not now.
- [2026-11-16 12:00] DNS override live; CA imported Windows Machine Root; CORS verified (same-origin no ACAO by design; cross-origin allowlist OK; disallowed 403).
- [2026-11-16 11:40] Spring env-var canonical mapping fix; grade B+ → A- with §12.4 evidence.
- [2026-11-16 15:10 (prev)] PRD synced to v1.1.0 (sa→ba); Boss directive: BA switch needs explicit Boss confirmation.
