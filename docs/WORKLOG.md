# Active Session Worklog & Handoff State

> Ultra-compact save point for stateless session handoffs. Always overwrite this file at the end of every stage checkpoint. Keep under 40 lines.

<!-- DO NOT REMOVE OR RENAME THESE 5 METADATA FIELDS -->
- **Project Name:** iam-service
- **Last Completed Stage:** Stage 7B pentest (2026-11-16, isolated throwaway env): 0 confirmed Medium+ findings; docs/10-PENTEST.md created; prod untouched
- **Next Active Stage:** Stage 7 close-out Boss attestation; soak v1.1.0 in prod; A/A+ blocked by accepted residuals; router DNS override + client CA import pending Boss
- **Active Skill to Load:** `senior-security-engineer`
- **Last Updated:** 2026-11-16 11:45

## 1. Key Decisions & Locked Artifacts
- **v1.1.0 (`417e266`) deployed + rollback proven 2026-11-16.** CI+drill green; backup verified; Flyway V12 auto-applied; 1.1.0→1.0.1→1.1.0 smoke UP. OPS account `ops` created (Boss cred); verify OK.
- **§6 fully checked 2026-11-16** — Boss ticked all boxes manually earlier; HTTPS box closed today with real TLS (in-app, internal CA "Osprey Internal CA", server cert CN `iam.osprey.service`, SAN DNS, 2026-11-16→2029-02-18, chain verify OK). Plain HTTP no longer served (400).
- **§12.4 item 7 met → grade A-** (honest position §12.7). A/A+ still blocked by accepted residuals §9: SEC-01 (DB 5433 open to LAN), SEC-02 (anonymous /actuator/prometheus over TLS, accepted).
- **HTTP(S) design:** no reverse proxy; Spring canonical env var names (dashes removed): `SERVER_SSL_ENABLED|KEYSTORE|KEYSTORETYPE|KEYALIAS|KEYSTOREPASSWORD`; keystore PKCS12 at host `E:\prod\iam-deploy\certs\` (ACL firma-only), mounted read-only `/certs`; env from `secrets\.env` (not in repo).
- **Client requirements:** router DNS override `192.168.18.250 → iam.osprey.service` (Boss action, pending); import `osprey-ca.crt` once per client; Windows schannel needs `--ssl-revoke-best-effort` (internal CA has no CRL/CDP) — documented in §14.
- Revocation caveat + alternatives (host CRL via reverse proxy) documented for future.

## 2. Open Items / Next
| Item | Owner | Note |
|------|-------|------|
| Router DNS override `iam.osprey.service` → 192.168.18.250 | Boss | gateway/DNS server; until set, clients need hosts entry |
| Import `osprey-ca.crt` in all client trust stores (browser + API); verify CORS origin uses https | Boss/client | required before browser clients work |
| Push docs commit (04-API-SPEC, .env.example, 07-DEPLOYMENT, WORKLOG, LL-011/012) | Boss | 3 commits pending decision |
| Soak v1.1.0 days; no load test ever run | Boss | gates any A claim |
| A/A+ closure paths (SEC-01 B1 unify networks; SEC-02 auth/firewall prometheus; CRL host; pen-test) | Boss | decisions, no date |

## 3. Exact Next Action (Start Here in New Session)
1. Boss adds DNS override + imports CA in client trust stores; smoke from a non-host client (browser JS login).
2. Boss decides push of docs commits; if green re-audit needed, sec re-checks §12.
3. Watch v1.1.0 prod stability (verify/409/401 metrics, Flyway v12 clean).

## 4. Decision & Switch Log
- [2026-11-16 | v1.1.0 + TLS] Boss "lanjut: tutup kotak HTTPS"; decisions: internal CA + hostname `iam.osprey.service` + TLS in-app; HTTP-only considered impossible later; HTTPS implemented env-var-driven (no rebuild); setup verified live (health UP over HTTPS, sessions/verify 200; plain HTTP → 400).
- [2026-11-16 15:10] PRD synced to v1.1.0 (sa→ba): added §3.10 security hardening, v1.1.0 ACs, honest §6/§7; check-id & check-bahasa PASS; Boss directive: every future switch to senior-business-analyst requires explicit Boss confirmation first.
- [2026-11-16 12:00] Boss finished pending: DNS override live (Resolve-DnsName OK -> 192.168.18.250), CA imported to Windows Machine Root store (Chrome/Edge/.NET-on-Windows covered); Firefox & Java need their own stores (B2/B-Firefox). Verified: HTTPS health UP without --resolve; CORS preflight+simple: same-origin = no ACAO (by design, Origin==Host), cross-origin allowed -> ACAO present, disallowed -> 403. Container recreated with new CORS_ALLOWED_ORIGINS (https origin). Docs uncommitted.
- [2026-11-16 11:40] Spring env-var canonical mapping trap fixed (`KEY_STORE` → `KEYSTORE` after UnrecoverableKeyException); CSRF/cert chain verified; schannel revocation caveat recorded; grade B+ → **A-** with full §12.4 evidence.
