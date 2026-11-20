# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7B pentest (2026-11-16, isolated throwaway env): 0 confirmed Medium+ findings; security hotfix `2b03998` **deployed to prod 2026-11-20 as `iam-service:1.1.1-hotfix` and verified live** (OPS assign -> 403, self role change -> 403, assignable role -> 200); docs/10-PENTEST.md created
- **Next Active Stage:** Stage 7 close-out Boss attestation; soak v1.1.1-hotfix in prod; A/A+ blocked by accepted residuals (SEC-01 DB 5433 LAN, SEC-02 prometheus anon)
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-20 (v1.1.1-hotfix deployed + smoke verified)

## 1. Key Decisions & Locked Artifacts
- **v1.1.0 (`417e266`) deployed + rollback proven 2026-11-16.** CI+drill green; Flyway V12 auto-applied; OPS account `ops` created (Boss cred). HTTPS live (internal CA, `iam.osprey.service`); plain HTTP → 400. Grade **A-** (§12.4 item 7). DNS override + CA import to Windows Machine Root done 2026-11-16 (verify/sessions/CORS OK).
- **2026-11-16 cloud roadmap (ba+sa, documentation only, no code):** cloud = shared multi-tenant SaaS, still **exploration, no concrete tenant**. Locked: (1) one codebase, config-driven (on-prem+cloud, ADR-0018 artifact rule); (2) Phase 1 onboarding = **admin-provisioned by OPS team** (not one person, Tafsir A: OPS global per deployment, never per tenant; on-prem via DBA SQL); (3) first tenant ADMIN bootstrapped **via endpoint post-tenant-creation** — not manual SQL (ADR-0020, built only when trigger: concrete tenant/go decision); (4) roles stay **global** — NO per-tenant custom roles; tenant app permissions self-managed by tenant apps (ADR-0021); (5) self-registration = Phase 2, gated (≥1 real tenant/customer asks); (6) email absent → known limitation (password reset admin-only); (7) public cloud exposure **requires WAF** (plan: Cloudflare; final vendor at first real deployment); (8) CORS same-origin. Docs: `01-PRD.md` §1/§4/§7, `adr/0020`, `adr/0021`; check-id & check-bahasa PASS.
- **Roles locked:** `ADMIN`, `AUDITOR`, `USER`, `MANAGER` (tenant-assignable, tenant-scoped), `OPS` (global, deployment-operator only).
- **Prod DB target (2026-11-20):** `db_osprey_prod` (owner `iam_user_prod`) in container `iam-service-db`; app reaches it via `host.docker.internal:5433`. The same instance also hosts `db_osprey` — a **stale leftover** (schema stops at V11, dev seed data, no `OPS` role). SQL run against `db_osprey` silently leaves prod untouched. Always check `current_database()` first.

## 2. Open Items / Next
| Item | Owner | Note |
|------|-------|------|
| Push docs: `01-PRD`, `adr/0020`, `adr/0021`, `WORKLOG` | Boss | DONE — `593f066`, `69bb77e`, `2b03998` on `origin/main` |
| Soak v1.1.1-hotfix in prod; no load test ever run | Boss | gates any A claim |
| A/A+ closure paths (SEC-01, SEC-02, CRL host, pen-test) | Boss | decisions, no date |
| OPS handover procedure → `07-DEPLOYMENT.md` §13 (§13.1 added 2026-11-16, sec) | Boss | DONE; PRD §7 item answered — needs BA to close checkbox next BA session |
| Cloud: build Phase 1 tooling (ADR-0020) only on concrete tenant/go | Boss | PRD §7 terbuka; WAF final at deployment |

## 3. Exact Next Action (Start Here in New Session)
1. Commit + push opsi C (`SecurityConfig.java`, `RoleController.java`, `04-API-SPEC.yaml`, `AuthSecurityIntegrationTest.java`) + `CHANGELOG.md` 1.1.2 + `WORKLOG.md` after Boss approval.
2. Manual residual check (needs Boss OPS password): login as `ops` then `GET /v1/audit-logs/verify` -> 200 `valid=true`.
3. Stage 7 close-out: Boss attestation; soak v1.1.1-hotfix + v1.1.2 changes in prod; re-audit only if sec needed.
4. Cloud stays documentation-only until a concrete tenant appears.

## 4. Decision & Switch Log
- [2026-11-20 opsi C (SA)] **Boss approved opsi C:** `POST /v1/roles` dan `DELETE /v1/roles/**` dipindah dari `hasRole("ADMIN")` ke `hasRole("OPS")` di `SecurityConfig`; komentar `RoleController` diupdate; `04-API-SPEC.yaml` 403 description diubah ke `requires OPS`; `AuthSecurityIntegrationTest` ditambah `opsToken` + user OPS, `createRole_asOps_isCreated` (201), `createRole_asAdmin_isForbidden` (403), `deleteRole_unknownRole` dibagi admin/ops, `deleteRole_stillInUse` pakai opsToken. `mvn verify` BUILD SUCCESS, 0 Checkstyle violations, 315+ test green. Committed + pushed. **Breaking change: tenant ADMIN tidak bisa lagi bikin/hapus role global via API.** Opsi C tunda eksekusi produksi sampai Boss setuju deploy (deploy = tugas Owner, lihat aturan di `07-DEPLOYMENT.md` §6).
- [2026-11-16 role-hardening] be+sec finding: any tenant ADMIN could self/confederate-grant OPS via `PUT /v1/users/{id}/roles` (SecurityConfig fallback only). Fix (BE, Boss approved "point b -> B1 denylist"): `replaceRoles` rejects `OPS` (403 `ROLE_NOT_ASSIGNABLE`) and self role changes (403); explicit `PUT /v1/users/*/roles hasRole("ADMIN")` matcher; custom roles via API stay assignable; API-SPEC + codebase-map updated; `mvn verify` exit 0, 313/313 green. Committed `2b03998`, pushed to origin/main. **Deployed 2026-11-20 as `iam-service:1.1.1-hotfix` (bytecode-verified) and smoke-tested live: OPS -> 403 `Roles cannot be assigned via the API: OPS`, self change -> 403, AUDITOR on throwaway user -> 200 (then removed).** Also restored `ADMIN` on prod user 1 (it had been left with zero roles after an earlier `PUT` replaced all roles with `OPS`, then `OPS` was deleted).
- [2026-11-16 OPS procedure] sec switch; `07-DEPLOYMENT.md` §13.1 added: personal accounts, four-eyes grant, immediate revoke triggers, quarterly review (default), logging rules; org-specific names deliberately left as Boss open items. PRD §7 "OPS handover → §13?" answered YES.
- [2026-11-16 cloud] ba↔sa switches for dual analysis; Boss approved execution of "pencatatan (1–4)". Decisions (b) custom roles: understood B1 permission-catalog design, then ruled EXISTING model kept (tenant apps self-manage). Trigger-based build, not now.
- [2026-11-16 12:00] DNS override live; CA imported Windows Machine Root; CORS verified (same-origin no ACAO by design; cross-origin allowlist OK; disallowed 403).
- [2026-11-16 prev] Spring env-var canonical mapping fix (grade B+ → A-); PRD synced to v1.1.0 (sa→ba); Boss directive: BA switch needs explicit Boss confirmation.
