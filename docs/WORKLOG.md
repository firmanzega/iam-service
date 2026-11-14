# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 3 - Solution Architect (ADR-0018, API-SPEC + ARCHITECTURE sync, uncommitted)
- **Next Active Stage:** Stage 4 - Backend Fixes (Option A: tenant-scoped identity; Boss decision pending on start)
- **Active Skill to Load:** `senior-backend-developer`
- **Last Updated:** 2026-11-14 14:35

## 1. Key Decisions & Locked Artifacts
- **Stage 7 (Security & Release):** `docs/07-DEPLOYMENT.md` and `CHANGELOG.md` v1.0.0 completed (committed); Package A conditional GO pending BUG-TENANT-001/004 resolution.
- **Stage 3 (Architecture):** Locked ADR-0016 (`tenant_id` / `X-Organization-ID`) and ADR-0017 (structured logging) in commit `638c1d3`; Scenario D (self-service) deferred.
- **Stage 4 & 6 (Implementation & QA):** Applied `V9__add_tenant_id.sql`, `TenantHeaderFilter`, and tenant-scoped repos (`63814ac`); fixed BUG-TENANT-002/003 (`8f125e8`); 249/249 tests green (`29ea651`).
- **Breaking contract approved (2026-11-14):** Option (a) — tenant-scoped identity `UNIQUE(tenant_id, username)` and `UNIQUE(tenant_id, email)`; `/v1/auth/login` and `/v1/auth/refresh` require `X-Organization-ID`. PRD updated (Stage 1, uncommitted); ADR + API-SPEC next (Stage 3).
- **Marketing:** Deployment targets on-premise (single `org-default` tenant) and cloud (multi-tenant shared schema) — reason for choosing Option (a).

## 2. Open Questions / Blockers for User
1. **BUG-TENANT-001:** Unknown organization currently falls back to `org-default` instead of rejecting with 401 per ADR-0016. Boss approved "Reject 401" on 2026-11-14 — pending implementation (Stage 4).
2. **BUG-TENANT-004:** Resolved as Option (a) in PRD (composite unique) — pending ADR + migration (Stage 3 + 4).
3. **PRD Sync:** Decided (2026-11-14) — PRD updated with multi-tenancy + structured logging (Stage 1 done, uncommitted).
4. **JWT Claim:** `tenant_id` claim in JWT is not yet used for tenant resolution (relies on header/fallback). Boss directs it into Stage 4 (see item in Stage 4 plan), then Stage 6, then Stage 7.
5. **Release version for breaking contract:** `1.1.0` or `2.0.0`? Decision needed at release time (see PRD §6).
6. **On-premise default:** Confirm single `org-default` tenant per on-premise install; cloud shares schema (ADR-0016 Alternative A).

## 3. Exact Next Action (Start Here in New Session)
1. Load skill `senior-backend-developer`.
2. Read ONLY `/docs/WORKLOG.md`, `/docs/ADR-0018` (`docs/adr/0018-*.md`), `/docs/04-API-SPEC.yaml`, and `/docs/08-CODEBASE-MAP.md`.
3. Create `V10__tenant_scoped_identity.sql` (drop global UNIQUE on users.username/email → composite `UNIQUE(tenant_id, username)` + `UNIQUE(tenant_id, email)`) and implement ADR-0018: login/refresh tenant resolution requires `X-Organization-ID`; update `/v1/auth/login` and `/v1/auth/refresh` filter paths; audit all 249 tests for missing header (login defaults to `org-default` when absent are NO LONGER VALID).
4. Update `TenantIsolationIntegrationTest` + `AuthSecurityIntegrationTest`; run full suite; commit Stage 1+3+4 artifacts.
5. Then resume per Boss: Stage 6 (QA retest) -> Stage 7 (release prep).

## 4. Decision & Switch Log
- [2026-11-14 | Stage 6 -> 4 -> 6] Fixed BUG-TENANT-002 (`requestURI` filter under MockMvc) and BUG-TENANT-003 (`user.getTenantId()` session attribution) in commit `8f125e8` — Reason: QA retest failures.
- [2026-11-14 | Stage 1] PRD updated for multi-tenancy + structured logging (check-id/check-bahasa clean); Boss approved Option (a) credit: marketing on-premise + cloud. — Reason: Boss decision, 2026-11-14.
- [2026-11-14 | Stage 3] ADR-0018 created: tenant-scoped identity, required `X-Organization-ID` on `/v1/auth/login` + `/v1/auth/refresh` (401 on unknown/missing org per ADR-0016 verification). API-SPEC + ARCHITECTURE updated; cspell clean (added `WORKLOG` to `cspell.json`). — Reason: Boss decision, Option (a).

