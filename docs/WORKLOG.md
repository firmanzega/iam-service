# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 6 - QA Retest (Multi-Tenancy)
- **Next Active Stage:** Stage 4 - Backend Fixes (Pending Boss Decision)
- **Active Skill to Load:** `senior-backend-developer`
- **Last Updated:** 2026-11-14 13:23

## 1. Key Decisions & Locked Artifacts
- **Stage 7 (Security & Release):** `docs/07-DEPLOYMENT.md` and `CHANGELOG.md` v1.0.0 completed (awaiting commit); Package A conditional GO pending BUG-TENANT-001/004 resolution.
- **Stage 3 (Architecture):** Locked ADR-0016 (`tenant_id` / `X-Organization-ID`) and ADR-0017 (structured logging) in commit `638c1d3`; Scenario D (self-service) deferred.
- **Stage 4 & 6 (Implementation & QA):** Applied `V9__add_tenant_id.sql`, `TenantHeaderFilter`, and tenant-scoped repos (`63814ac`); fixed BUG-TENANT-002/003 (`8f125e8`); 249/249 tests green (`29ea651`).

## 2. Open Questions / Blockers for User
1. **BUG-TENANT-001:** Unknown organization currently falls back to `org-default` instead of rejecting with `401/404` per ADR-0016. Which behavior should be enforced?
2. **BUG-TENANT-004:** Login lookup (`username=? or email=?`) ignores tenant scope despite repository comments claiming tenant filtering. How should duplicate usernames across tenants be resolved?
3. **PRD Sync:** `docs/01-PRD.md` does not yet cover multi-tenancy or structured logging. Should Stage 1 (`senior-business-analyst`) be updated?
4. **JWT Claim:** `tenant_id` claim in JWT is not yet used for tenant resolution (relies on header/fallback). Which stage should handle this?

## 3. Exact Next Action (Start Here in New Session)
1. Load skill `senior-backend-developer` (and `senior-business-analyst` if PRD sync is approved).
2. Read ONLY `/docs/WORKLOG.md`, `/docs/03-ARCHITECTURE.md` (ADR-0016), and `/docs/08-CODEBASE-MAP.md`.
3. Apply Boss's decisions for BUG-TENANT-001/004, re-run the test suite, and commit updated code and docs.

## 4. Decision & Switch Log
- [2026-11-14 | Stage 6 -> 4 -> 6] Fixed BUG-TENANT-002 (`requestURI` filter under MockMvc) and BUG-TENANT-003 (`user.getTenantId()` session attribution) in commit `8f125e8` — Reason: QA retest failures.
