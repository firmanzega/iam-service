# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** v1.1.0 hardening code complete & verified (2026-11-16); docs synced (ADR-0019, API-SPEC 1.1.0, CHANGELOG [1.1.0], 07-DEPLOYMENT residuals+§13, 03-ARCHITECTURE)
- **Next Active Stage:** v1.1.0 deploy + rollback exercise w/ Boss on host; then re-audit vs §12.4 (A- path); then commit & push pending Boss
- **Active Skill to Load:** `senior-security-engineer` (deploy/rollback/re-audit)
- **Last Updated:** 2026-11-16 10:30

## 1. Key Decisions & Locked Artifacts
- **ADR-0019 (binding token↔tenant, option a):** every authenticated request (except `/v1/auth/login`, `/v1/auth/refresh`) requires JWT `tenant_id` claim == resolved header tenant else 401 `tenant_mismatch` (recorded by `SecurityEventRecorder`). Refresh stays anchored to stored token tenant (ADR-0018 pt 6). Multi-org membership (option b / `tenant_users`) rejected for v1.1.0.
- **Audit verify = OPS role:** `GET /v1/audit-logs/verify` now `hasRole('OPS')` (was ADMIN). V12 seeds `OPS` role, assigned to nobody. Ops account must be created manually (see 07-DEPLOYMENT §13). Breaking for ADMIN callers of verify (documented in CHANGELOG [1.1.0] & API-SPEC).
- **token_type enforced at decoder:** refresh token can no longer be used as Bearer (JwtKeyRing + `JwtValidators`/`JwtClaimValidator("token_type"="access")`).
- **TenantHeaderFilter** records 401 missing/unknown-org (metric `iam.security.rejected`, aggregate `tenant_mismatch`-style reasons `missing_organization`/`unknown_organization`) + body now unified `ErrorResponse`; filter moved BEFORE `BearerTokenAuthenticationFilter`; new `TenantBindingFilter` AFTER it.
- **Retention fix:** `security_rejection_stats` cleanup now uses global `deleteOlderThan(cutoff)` (was tenant-scoped to org-default only).
- **TenantContext** `InheritableThreadLocal` → `ThreadLocal` (no child-thread leakage).
- **SEC-01 accepted (Boss, internal LAN):** DB `5433` published `0.0.0.0` — documented as accepted residual in 07-DEPLOYMENT §9; closure plan = unify Docker networks when going public. **SEC-02:** `/actuator/prometheus` stays `permitAll` (Boss Option 1), documented §9.
- **Go verify green:** `mvn clean verify` BUILD SUCCESS, **310/310** (incl. new `TenantBindingIntegrationTest`), cspell 0/229 files.

## 2. Open Items / Next
| Item | Owner | Note |
|------|-------|------|
| Commit v1.1.0 changes (feat + breaking verify-permission change) | Boss decides | repo dirty: src, tests, config/spotbugs, docs (ADR-0019, 03/04/07, 08 unchanged, CHANGELOG, WORKLOG), cspell-id.json |
| Deploy v1.1.0 to host `192.168.18.250:9090` + rollback exercise (Item 9) | Boss + `sec` | rollback must be executed against real env for A- |
| Re-audit vs §12.4 → claim A- (Item 10) | `sec` | after deploy |
| Post-deploy §6 checklist fully checked; WORKLOG re-audit honestly | `sec` | |

## 3. Exact Next Action (Start Here in New Session)
1. Ask Boss: commit plan for v1.1.0 (single feat commit vs split hardening/docs); then push & CI green.
2. With Boss present: deploy v1.1.0 to LAN host; verify login + `/v1/auth/sessions` + `/v1/audit-logs/verify` (OPS); execute rollback exercise; log evidence in WORKLOG.
3. Re-audit vs `07-DEPLOYMENT.md` §12.4; if green, A- claimable.

## 4. Decision & Switch Log
- [2026-11-16 | v1.1.0] sa → be → qa → (sa→sec→sa) execution complete for hardening items 1–8. ADR-0019 written; verify-run & cspell green; live host untouched (SEC-01 left as-is per Boss).
- [2026-11-16 09:58] One test fixed by policy change (`SecurityRejectionStatsServiceTest` tenant-scoped→global); `JwtTestTokens` now carries `tenant_id=1` (required by binding filter); `UserConcurrencyTest` sets context in worker threads (no more inheritance); `AuditChainVerificationIntegrationTest` verify calls use new OPS user + ADMIN gets 403 test.
- [2026-11-16 09:57] Binding path confirmed: `TenantHeaderFilter`(before bearer) → validate JWT → `TenantBindingFilter`(after bearer) enforces claim==context; login/refresh exempt.
