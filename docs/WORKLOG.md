# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** v1.1.0 deployed to LAN host + rollback exercised (2026-11-16); docs re-audited (07-DEPLOYMENT §6/§9/§12.7)
- **Next Active Stage:** Stage 7 close-out — Boss attestation for remaining §6 items; then A- re-audit (§12.4); v1.1.0 soak in prod
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-16 11:00

## 1. Key Decisions & Locked Artifacts
- **417e266 pushed to main 2026-11-16; CI green** (CI + restore drill both success on the SHA).
- **Deploy executed 2026-11-16 (Boss present):** `iam-service:1.1.0` built on host; prod compose tag 1.0.1→1.1.0; Flyway V12 auto-applied ("now at version v12"); app recreated; smoke 200 (health UP, login, /v1/auth/sessions, /v1/audit-logs/verify valid=true 8 entries).
- **Rollback exercised:** 1.1.0 → `iam-service:1.0.1` redeploy (health UP, login 200) → back to `1.1.0` (health UP, verify 200). Compose currently at `1.1.0`.
- **Pre-deploy backup:** `backup-v1.1.0-pre-deploy.dump` (SHA-256 `716ed58b70612eb173a60f1bd7640515c257cf0bb35be6e1307d49930636015d`), 102 TOC entries, custom format — verified listed.
- **OPS account created** (username `ops`, password chosen by Boss out-of-band); V12 seeded role OPS; user assigned OPS (verified: `ops | OPS`). Verify endpoint now usable by operator only; ADMIN/AUDITOR → 403.
- **v1.1.0 scope shipped:** ADR-0019 token↔tenant binding; `token_type=access` enforced; verify=OPS; rejection-stats retention global; ThreadLocal fix; unified ErrorResponse for header 401s; mvn verify 310/310; cspell 0.
- **SEC-01 accepted by Boss (internal LAN):** DB `0.0.0.0:5433` stays published; closure = unify Docker networks when going public (B1). **SEC-02 verified live:** `/actuator/prometheus` anonymous 200 OK ~50KB; accepted (internal LAN); doc in §9.

## 2. Open Items / Next
| Item | Owner | Note |
|------|-------|------|
| §6 remaining boxes (JWT_SECRET/POSTGRES_PASSWORD rotation evidence, HTTPS/CORS for real client, prometheus visibility, gateway policy) | Boss | all prod-only attestations |
| A- claimable? — needs §6 fully checked + §12.4 item 7 | Boss + `sec` | item 6 (rollback) DONE |
| Soak v1.1.0 in prod (days) before any grading claim | Boss | no load test was run |
| Push status: `417e266` on `origin/main`; docs changes (07-DEPLOYMENT §6/§9/§12.7, this file) NOT yet committed | Boss | commit pending decision |

## 3. Exact Next Action (Start Here in New Session)
1. Boss attests remaining §6 items (or accepts them as unchecked); `sec` updates `07-DEPLOYMENT.md` §6 + §12.7 honestly.
2. Commit docs re-audit changes (CHANGELOG untouched; already covers v1.1.0).
3. Re-audit vs §12.4 items 5-7; if green → A- claimable with evidence in WORKLOG.

## 4. Decision & Switch Log
- [2026-11-16 | v1.1.0 deploy] Boss "lakuin ini" (full plan approved). Sequence: backup → build → deploy → smoke → rollback exercise (1.0.1) → redeploy 1.1.0. OPS account created with Boss credentials (never written to repo/docs). Reason: close A- items 5-6.
- [2026-11-16 10:18] Backup `backup-v1.1.0-pre-deploy.dump` verified; [10:20] compose tag 1.1.0; Flyway V12 on prod (docker logs); [10:19] smoke UP/login200/verify200; [10:30] rollback 1.0.1 smoke UP; [10:31] redeploy 1.1.0 smoke UP + verify 200 (8 entries); [10:35] §6/§9/§12.7 updated honestly (grade stays B+; item 6 closed).
