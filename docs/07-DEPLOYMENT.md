# Deployment Guide — IAM Service v1.0.0

> English (technical documentation per `team-workflow` standard).
> Output of **Phase 7** (`senior-security-engineer`).

---

## 1. Secret Management

**Main rule: secret must never be on server disk, must not enter repo, and must not enter image/build artifact.**

| Secret | Environment variable | Recommended source |
|--------|----------------------|--------------------|
| JWT signing key | `JWT_SECRET` | Secret manager or CI/CD, minimum 32 bytes |
| JWT previous key | `JWT_SECRET_PREVIOUS` | Secret manager or CI/CD, only during key rotation transition |
| Database password | `POSTGRES_PASSWORD` | Secret manager or CI/CD |
| Database name/user | `POSTGRES_DB`, `POSTGRES_USER` | Environment variable |
| Database host/port | `POSTGRES_HOST`, `POSTGRES_PORT` | Environment variable |
| CORS origins | `CORS_ALLOWED_ORIGINS` | Environment variable, explicit list (no wildcard) |

File `.env.example` in root contains only **placeholder**, no real credentials. File `.env` is excluded by `.gitignore` and by githook that rejects committing file `.env`.

**Secret that has been leaked is considered compromised:** revoke and rotate immediately. Deleting commit is not enough because git history and copies still exist.

---

## 2. Docker Image

| Aspect | Configuration |
|-------|-------------|
| Base image | `eclipse-temurin:21-jdk` (build), `eclipse-temurin:21-jre` (runtime) |
| User | `10001:10001` (non-root, not root) |
| Port | `9090` (production profile) |
| Profile | `SPRING_PROFILES_ACTIVE=prod` |
| Memory | `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"` via ENTRYPOINT |
| Architecture | Multi-stage: build stage builds jar, runtime stage only JRE + jar |

Image built via GitHub Actions on every push/PR, not pushed to any registry (no release registry yet).

```bash
docker build -t iam-service:latest .
docker run --rm -p 9090:9090 \
  -e JWT_SECRET="$JWT_SECRET" \
  -e POSTGRES_HOST=... \
  iam-service:latest
```

---

## 3. Prometheus Exposure (critical security)

`/actuator/prometheus` is exposed **without token**, because scraper cannot update 15-minute access token.

**Must only be reached by internal scraper network.** Do not publish via public gateway. Requirements:
- Service must not be reachable directly from public network.
- Use internal reverse proxy, and register proxy address at `server.tomcat.remoteip.internal-proxies` if needed.

Exposed metrics: `iam_security_rejected_total`, `http_server_requests_seconds_count`, `jvm_memory_used_bytes`, `hikaricp_connections_pending`, and default JVM/HTTP metrics.

---

## 4. HTTP Security Headers

Apply via `SecurityConfig`:

| Header | Value | Purpose |
|--------|-------|---------|
| Content-Security Policy | `default-src 'self'; script-src 'self' 'unsafe-inline'; ...; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` | Blocks external script/style/image sources |
| Strict-Transport-Security | `max-age=31536000; includeSubDomains` | Force HTTPS |
| X-Frame-Options | `DENY` | Prevent clickjacking |
| X-Content-Type-Options | `nosniff` (Spring Security default) | Prevent MIME sniffing |

In production profile, springdoc/Swagger UI is completely disabled, no HTML page exposed.

---

## 5. Rate Limit & Authentication

- Rate limiter per IP on authentication endpoints (`/v1/auth/login`, `/v1/auth/refresh`), limit 20 requests per minute per IP address.
- Rate limiter is **per instance**, not distributed. If more than one instance, effective limit is several times looser. Mitigation: per-account account lock (5 failures → lock 15 minutes), and place global rate limit on gateway/reverse proxy.
- Header `X-Forwarded-For` only used when connection comes from internal proxy (`server.forward-headers-strategy=native`). Ensure service **cannot** be reached directly from public.

---

## 6. Checklist Before Going Public

- [x] `SPRING_PROFILES_ACTIVE=prod` set (prod environment) — verified 2026-11-16: compose `docker-compose.prod.yml` runs `iam-service:1.1.0` (Dockerfile bakes `prod`); `/v3/api-docs` -> 404, port 9090, Flyway validate-on-migrate OK
- [x] `JWT_SECRET` replaced with random value, minimum 32 bytes, not in any file on server (prod environment)
- [x] `JWT_SECRET_PREVIOUS` empty, except during key rotation transition
- [x] `POSTGRES_PASSWORD` changed, not in any file (prod environment)
- [x] Database port not open to internet (prod environment)
- [x] HTTPS active, certificate valid (prod environment) — 2026-11-16: TLS terminates in-app (no proxy); PKCS12 keystore issued by internal Osprey CA (`CN=Osprey Internal CA`); server cert SAN=DNS:`iam.osprey.service`, valid 2026-11-16 → 2029-02-18, chain verify OK (`openssl verify`); plain HTTP on 9090 no longer servable (Tomcat HTTPS connector only, HTTP request returns 400); smoke: login 200, `/v1/auth/sessions` 200, `/v1/audit-logs/verify` valid=true. See §14.
- [x] Production database does not contain default `admin` account from dev profile (verify after first boot on prod DB)
- [x] `CORS_ALLOWED_ORIGINS` filled with correct domain, and browser clients send `X-Organization-ID` (CORS policy allows the header) (prod environment + client config)
- [x] Clients updated to send `X-Organization-ID` on login/refresh and all tenant-aware endpoints (missing/unknown organization returns 401) (client migration task)
- [x] Flyway migrations V9–V11 applied and validated — applied automatically at first boot (`validate-on-migrate: true`), verified on dev DB (schema now at v11) and on CI's Testcontainers. **v1.1.0 (2026-11-16): V12 `seed_ops_role` applied at first boot on prod DB (confirmed in `docker logs`: "Migrating schema public to version 12 - seed ops role ... now at version v12"); `iam-service` recreated and smoke-tested (health UP, login 200, `/v1/auth/sessions` 200, `/v1/audit-logs/verify` 200 with OPS account: `valid=true`, 8 entries).**
- [x] `iam-service:1.1.0` deployed 2026-11-16 and **rollback exercised**: 1.1.0 deployed (smoke UP) → `1.0.1` redeployed (`health UP`, login 200) → `1.1.0` redeployed (health UP, verify 200). Evidence in WORKLOG (2026-11-16 entry) and backup `backup-v1.1.0-pre-deploy.dump` (SHA-256 `716ed58b70612eb173a60f1bd7640515c257cf0bb35be6e1307d49930636015d`)
- [x] `iam-service:1.1.1-hotfix` deployed 2026-11-20 (security hotfix, commit `2b03998`): image rebuilt (bytecode verified to contain the OPS restriction), `docker-compose.prod.yml` image tag bumped, `app` service recreated (health UP, startup clean, no new migration). Smoke: `PUT /v1/users/2/roles` with `OPS` -> **403 `Roles cannot be assigned via the API: OPS`**; self role change -> **403**; assignable role (`AUDITOR`) on a throwaway user -> 200 then removed. Rollback: `docker-compose.prod.yml.bak-2026-11-20-pre-hotfix` + recreate `app`
- [x] `/actuator/prometheus` only available on internal network, not via public gateway (prod environment)
- [x] GitHub Actions green on tag `v1.0.1` HEAD `4ebec1e` (CI + restore drill SUCCESS on push events, verified 2026-11-14; also green on `ed35cb1`) and restore drill #13-#15 SUCCESS
- [x] Container image successfully built and run as non-root (multi-stage Dockerfile, `USER 10001:10001`; CI builds the image)
- [x] Scheduled database backup has been tested for recovery — proven by the restore drill (`.github/workflows/restore-drill.yml`): seed → dump → destroy → restore → assert rows/indexes/constraints; also green on tag `v1.0.1` HEAD `4ebec1e` (run 43600698525)
- [x] `iam.jwt.refresh-token-ttl` reviewed (7 days; consistent with docs; change only with documented reason)
- [x] `iam.security-rejection-stats.retention` matches local retention policy (30d default, documented in `application.yaml`)
- [x] Session and 401 aggregate cleanup enabled, or scheduled outside the application (`SessionCleanupJob`, `RefreshTokenCleanupJob`, `SecurityRejectionStatsCleanupJob` enabled, 1h interval)
- [x] Tracing collector endpoint filled if tracing enabled, or sampling `0.0` (sampling default `0.0`; OTEL endpoint documented in `application.yaml`)

---

## 7. Database Migration

Flyway migration is the single source of truth for schema. `V1` through `V11` are applied automatically at first boot; production runs `ddl-auto: validate` and `validate-on-migrate: true`, so the service refuses to start against a drifted schema. Migration is idempotent and forward-only: never edit an applied migration file.

| Migration | What it does | Rollback |
|-----------|--------------|----------|
| V1–V8 | Base schema: users, roles, RBAC, audit chain, sessions, rejection-stats | Restore from backup (forward-only) |
| V9 | Adds `tenant_id` + `tenants` registry; backfills existing rows to `org-default` | Restore from backup; keep column, only safe additions |
| V10 | Drops global `UNIQUE` on `users.username`/`email`; adds `UNIQUE(tenant_id, username)` and `UNIQUE(tenant_id, email)` (ADR-0018) | Re-create global unique **only** if all tenants hold no same-identity users |
| V11 | Drops global `uq_security_rejection_stats` (window_start, method, reason); adds `UNIQUE(tenant_id, window_start, http_method, reason)`; enables the single-statement upsert (SEC-STATS-001) | Re-create the global unique **only before any second tenant exists** |

Deploy order: **backup → apply migrations on one instance (or stop others) → roll out new instances**. For live databases created before V9, apply V9 → V10 → V11 in order; for fresh databases everything applies in one pass.

**Rollback overall:** restore from backup (Flyway Community has no down migrations); or redeploy the previous image against a restored database. V11's rollback is only safe before cloud onboarding — see the warning in `V11__tenant_scoped_rejection_stats_unique.sql`.

Access tokens issued before migration remain valid; `X-Organization-ID` is a new **required** header on login/refresh, so clients must be updated before or at deploy time (see §10).

**Know which database is production.** The production database is `db_osprey_prod` (owner `iam_user_prod`) inside the `iam-service-db` container, reached from the app through `host.docker.internal:5433`. The same PostgreSQL instance also hosts `db_osprey`, which is a **stale leftover** (schema stops at V11, dev seed data, no `OPS` role). Running SQL against `db_osprey` by mistake silently "succeeds" while production is untouched. Always confirm the target first:

```sql
SELECT current_database(), (SELECT max(version::numeric) FROM flyway_schema_history) AS schema_version;
-- expected for production: db_osprey_prod | 12
```

---

## 8. Backup & Recovery

Scripts in `scripts/`:
- `backup-postgres.sh` — custom format backup with `pg_dump`, prints SHA-256
- `restore-postgres.sh` — restores backup, rejects without `--yes`

Both can run inside the database container or via PostgreSQL client on the host. Test recovery to a separate database periodically. No cron example is committed in this repository yet — the GitHub Actions restore drill (`.github/workflows/restore-drill.yml`) is the reference implementation of a scheduled backup-verify cycle.

Automated restore test runs in GitHub Actions (`.github/workflows/restore-drill.yml`): back up → delete data → restore → verify rows, indexes, and unique constraints.

---

## 9. Security Audit (re-audit 2026-11-14, Phase 7 redo for the multi-tenancy breaking contract)

Full audit across the repository (OWASP Top 10, IDOR, secret leakage, dependencies, transport, config). Method: source→sink tracing for the new tenant boundary, targeted config review, dependency review via CI.

### Findings and handling

| Area | State (verified 2026-11-14) |
|------|------------------------------|
| Secrets | No secrets in repo or git history; `.env` gitignored and never committed; `.env.example` contains placeholders only; `JWT_SECRET` minimum length 32 enforced at startup |
| CORS | Explicit origins only; no wildcard with credentials; `X-Organization-ID` added to allowed headers for browser clients |
| Prod profile | `springdoc` disabled; `ddl-auto: validate`; dev-only seed and `DevAdminBootstrap` are `@Profile("dev")`; actuator exposure limited to health/metrics/prometheus |
| JWT | HS256 with explicit algorithm (no `none`/confusion); min 32 bytes; `tenant_id` claim carried but **never read** for authorization decisions (resolution is header-based, ADR-0018 §6). `WebMapper.tenantId` has zero callers — dead code; backend cleanup noted, not a security issue |
| Tenant isolation | Tenant-scoped repositories for users/sessions/tokens; unknown or missing organization → 401 on every tenant-aware path; `UNIQUE(tenant_id, username/email)`; cross-tenant access covered by `TenantIsolationIntegrationTest` |
| Input handling | All request bodies are validated DTOs (`@Valid`); SQL via parameterized queries/JPA only; page size capped at 50; pagination serialization DTO-only |
| Logging | No user-controlled strings logged; audit details stored raw but never printed; log injection surface reviewed, none found |
| Supply chain | CI enforces Trivy HIGH/CRITICAL on the image, weekly dependency review, `dependency-review` on PRs, restore drill; commit `56fa3fc` note: `mvn clean verify` green (299/299, JaCoCo ≥80% per class, Checkstyle 0, SpotBugs 0) |
| Docker | Multi-stage build; non-root 10001:10001; JRE runtime; `SPRING_PROFILES_ACTIVE=prod` baked into image |

### Residual risks (accepted, intentionally documented)

| Risk | Acceptance reason |
|--------|-----------------|
| SEC-01: database port `5433` published on all host interfaces (`0.0.0.0:5433->5432`), reachable from the LAN; host firewall has no deny rule (Docker Desktop Backend rule allows Any/Any on Public profile). Postgres `pg_hba` offers password-only auth to the world on that port | Accepted 2026-11-16 by Boss: deployment is internal-only, not public; app and DB share the host. Closure plan when going public/cloud: unify app+db on one Docker network and stop publishing 5433 (see WORKLOG SEC-01). Minimum interim control: strong, unique `POSTGRES_PASSWORD` |
| SEC-02: `/actuator/prometheus` exposed without token (`permitAll`) on `192.168.18.250:9090`, reachable on the LAN (verified 2026-11-16: anonymous GET returns 200, ~50 KB body) | Accepted 2026-11-16 by Boss (Option 1 of v1.1.0 scope): LAN-internal, must not be routed to public networks; OIDC/basic-auth options rejected as overkill for a single host. Closure plan: restrict scraper source IP via Windows Firewall rule or reverse proxy with auth |
| Access token cannot be revoked instantly | Maximum 15-minute window; direct access token revocation requires a central revocation list that does not exist yet |
| Rate limiter per instance | Gateway mitigation; per-account account lock still works |
| 401 does not trace the actor | Requests without credentials can be sent by anyone; flood to audit_logs is riskier |
| Hash chain is not an absolute guarantee | Database write access can recalculate the chain; periodic copy to append-only storage is recommended |
| V10/V11 rollback only before second tenant onboards | Restoring the global uniques after cloud cutover is unsatisfiable |

## Appendix A — Audit Phase 7 (Package A, prior pass, kept for history)

Audit performed across the entire repository (OWASP Top 10, IDOR, secret leakage, dependencies).

### Findings already addressed (no code fix needed)

| Area | Finding | Handling |
|------|---------|----------|
| JWT | Algorithm confusion (`none`) | Rejected by NimbusJwtDecoder with explicit `MacAlgorithm.HS256` |
| JWT | Weak key | Rejected at startup if less than 32 bytes |
| Error response | Stack trace leaks to client | Generic messages (`Authentication required`, `Access denied`), no exception details |
| 401/403 | IDOR on sessions | Sessions filtered by owner, other user's session returns 404 (indistinguishable from non-existent) |
| 401 | Flood audit_logs | 401 is not written to audit_logs; only WARN log, metric, and per-minute aggregate |
| Secret | Hardcoded in repo | Not found; all from environment variables, `.env.example` is only a placeholder |
| Docker | Running as root | Image runs as user 10001:10001 |
| CI | Image not scanned | Trivy scans image, blocks HIGH/CRITICAL with available fixes |

### Residual risks (accepted, intentionally documented)

| Risk | Acceptance reason |
|--------|-----------------|
| Access token cannot be revoked instantly | Maximum 15-minute window; direct access token revocation requires a central revocation list that does not exist yet |
| Rate limiter per instance | Gateway mitigation; per-account account lock still works |
| 401 does not trace the actor | Requests without credentials can be sent by anyone; flood to audit_logs is riskier |
| Hash chain is not an absolute guarantee | Database write access can recalculate the chain; periodic copy to append-only storage is recommended |

---

## 10. Release Notes — Breaking Contract (v1.0.0)

Multi-tenancy ships in the first release (Option B, Boss ruling 2026-11-14): v1.0.0 was never tagged, published, or deployed anywhere, so the breaking changes are folded into the initial release instead of forcing a v2.0.0 or an unshipped v1.1.0.

Client migration steps before deploying:
1. **Send `X-Organization-ID` on every tenant-aware request** — required by `POST /v1/auth/login`, `POST /v1/auth/refresh`, and every `/v1/users/*` operation (list, detail, create, update, patch, password changes incl. self-service, roles, activate/deactivate, reset-password); missing/unknown organization → 401 (unknown-org rejection was extended to all tenant-aware endpoints).
2. **On-premise (single-tenant) clients** set the header to `org-default` (or the operator-provided organization id); **cloud clients** use their own `organization_id`.
3. **Account migration:** identities are now per-tenant unique. Existing single-tenant deployments are unaffected; multi-tenant operators must verify no global-unique assumptions in their data tooling (e.g., ETL upserts by username).
4. Unknown/missing organization rejects with 401 uniformly (no silent `org-default` fallback anywhere).
5. Deployment order: **backup → migrate (Flyway auto, single instance first) → roll out**. Rollback past V10/V11 only before a second tenant exists.

## 11. Security Incident Runbook

1. **Revoke access immediately.** Replace `JWT_SECRET` with a new key and empty `JWT_SECRET_PREVIOUS`, then restart all instances. All old access tokens are immediately rejected.
2. **Revoke all refresh tokens and sessions** in the database:
   ```sql
   update refresh_tokens set revoked_at = now() where revoked_at is null;
   update sessions set revoked_at = now() where revoked_at is null;
   ```
3. **Check the audit log** (`ACCESS_DENIED`, `TOKENS_REVOKED`, `SESSION_REVOKED`) and the 401 aggregate for suspicious activity.
4. **Rotate the database password** if database credentials are suspected leaked.
5. **Record the incident** in `CHANGELOG.md` according to the reporting policy.

---

## 12. Release Grading Rubric (project-internal)

> Status: project-internal and ad-hoc. This is NOT an industry standard and implies no external certification. It exists so release grades (B+, A-, A, A+) are traceable to evidence instead of sentiment. If this rubric and reality disagree, reality wins and this file must be corrected.

### 12.1 Grading rules

1. **Grade = lowest unmet mandatory criterion.** No averaging, no rounding up, no partial credit.
2. **Evidence or it did not happen.** Every criterion must name objective evidence (CI run id, drill id, doc section, command output). Claims without evidence are not graded.
3. **Accepted residual risks (Section 9) block A and A+** until actually closed or explicitly re-negotiated with Boss and re-documented in this file.
4. **Regression drops the grade immediately.** If a previously met criterion goes red (e.g., `mvn clean verify` fails, drill red), the grade falls and the change is recorded in `docs/WORKLOG.md`.
5. **Claims live in WORKLOG** with pointers to this section. Outsiders should read the grades as internal shorthand, not as certification.

### 12.2 Baseline gates (required for any release grade)

- `mvn clean verify` BUILD SUCCESS; coverage >= 80% per class; Checkstyle 0; SpotBugs 0 (evidence: `06-TEST-PLAN.md` QA-001..QA-013, CI runs).
- CI green on the release SHA, including Trivy (no unfixed HIGH/CRITICAL) and dependency-review.
- Restore drill green (`.github/workflows/restore-drill.yml`).
- No secrets in repo or history; `.env.example` placeholders only; Docker image non-root.
- `docs/WORKLOG.md` reflects the true state at the time of the claim.

### 12.3 B+ (current honest baseline, 2026-11-14)

Baseline gates are green, but the known debt register is open: `04-API-SPEC.yaml` documents only `/auth/*`; `WebMapper.tenantId` dead code; Dependabot PRs #1-#4 red (pre-fix branches); cspell red repo-wide (Indonesian docs not in `ignorePaths`); no live deploy or rollback exercised; Section 6 prod items unchecked; sentinel mutation tests are a subset.

### 12.4 A- (release-ready; close the known register)

Mandatory, all with evidence:

1. `04-API-SPEC.yaml` documents ALL exposed `/v1` endpoints (auth, users, roles, audit).
2. Dead public code flagged by audit removed (`WebMapper.tenantId`); `mvn clean verify` green.
3. Zero open Dependabot PRs on the default branch; each merged only after a rebase onto `main` and a green CI run.
4. cspell (repo `cspell.json`) reports 0 issues repo-wide; Indonesian docs handled via `ignorePaths` or an `id` locale, never a mass rewrite.
5. Go-live executed per Section 6 with prod-only items checked against the real production environment; smoke (`/actuator/health` + login) passed; evidence recorded in WORKLOG.
6. Deploy AND rollback both exercised at least once against the real environment.
7. `docs/07-DEPLOYMENT.md` Section 6 fully checked; WORKLOG re-audited honestly post-deploy.

### 12.5 A (proven in operation, not just on paper)

All A- criteria, plus:

1. Contract tests enforce API-SPEC vs implementation in CI; drift fails the build.
2. Mutation testing covers all sentinel/security test classes (not a subset); threshold documented.
3. Penetration test executed (Stage 7B); all Critical/High findings closed and re-tested.
4. Tracing exported to a real OTLP collector (sampling > 0 in prod); at least one alert fired and observed end-to-end.
5. Backup/restore tested against a prod-like data volume; measured RPO/RTO documented in Section 8.
6. SBOM published as a release artifact.

### 12.6 A+ (risk closure; requires new decisions and infrastructure)

All A criteria, plus explicit closure of the Section 9 accepted residuals:

1. Access tokens revocable in near-real-time (central revocation list or introspection) — closes residual A-1 (Section 9).
2. Distributed rate limiting (gateway or shared store) — closes residual A-3 (Section 9).
3. 401 rejection traceability implemented with a documented privacy trade-off — closes residual A-2 (Section 9).
4. Audit hash chain anchored to append-only/WORM external storage.
5. Secrets sourced from a secret manager at runtime; no credentials on server disk.
6. Multi-instance behavior verified (rate limiter, cleanup jobs, session consistency).

### 12.7 Honest position (2026-11-16, v1.1.0 post-TLS re-audit)

Current grade: **A-** (2026-11-16). All §12.4 mandatory criteria are met with evidence:

1. API-SPEC documents all exposed `/v1` endpoints (auth, users, roles, audit; 16 paths / 20 operations; mechanical match against controllers) — DONE (2026-11-14, `23e7052`).
2. Dead public code removed (`WebMapper.tenantId`, `d28f832`); `mvn clean verify` green (310/310 on `417e266`) — DONE.
3. Zero open Dependabot PRs — DONE (2026-11-14).
4. cspell 0 issues repo-wide (229 files, 0 issues, 2026-11-16) — DONE.
5. Go-live per §6 with prod-only items checked against the real environment; smoke (`/actuator/health` UP + login) passed — DONE for v1.1.0 on 2026-11-16 (deploy + login + sessions + verify).
6. Deploy AND rollback both exercised against the real environment — DONE 2026-11-16 (1.1.0 → 1.0.1 → 1.1.0, all smoke-tested UP).
7. §6 fully checked (HTTPS closed 2026-11-16: TLS in-app, internal CA, SAN `iam.osprey.service`; plain HTTP no longer served) + this file re-audited honestly — DONE 2026-11-16.

Grade rule §12.1 confirms A- (lowest unmet criterion = A- criteria all met; A/A+ deliberately not claimed). **A and A+ remain blocked** by accepted residuals in §9 (SEC-01: DB port 5433 open to LAN; SEC-02: unauthenticated `/actuator/prometheus`, now served over TLS but still anonymous) and by §12.5/A items (env-installs: pen-test, contract tests in CI, OTLP in prod with ≥ one fired alert, measured RPO/RTO, published SBOM) — none of which are in scope for this release. Also not claimed: no load/performance test was run; v1.1.0 has been in production only since 2026-11-16 (smoke window); no third-party security audit.

---

## 13. OPS Role Operations (v1.1.0)

`GET /v1/audit-logs/verify` is restricted to the global `OPS` role (seeded by migration V12, deliberately assigned to no user). Tenant ADMIN/AUDITOR tokens receive 403.

**Create (or rotate) an OPS user.** Run inside the database container or via a PostgreSQL client on the host. Prefer a dedicated OPS account owned by the operator, not a reuse of any tenant admin account:

```sql
-- assign the OPS role to an existing user
INSERT INTO user_roles (user_id, role_id)
SELECT id, (SELECT id FROM roles WHERE name = 'OPS')
FROM users WHERE username = '<ops-username>'
ON CONFLICT DO NOTHING;
```

The dedicated account is the intended shape: OPS can read the cross-tenant audit chain, so it must not belong to any organization. Log every OPS grant/revoke in the change log (`CHANGELOG.md` or a deployment ticket).

**Break-glass note:** if nobody holds OPS, audit verification from outside the database is impossible — verify offline with direct SQL plus the verifier logic, and record it. Do not widen the endpoint back to ADMIN to make verification convenient.

### 13.1 OPS grant, revoke, and handover procedure (2026-11-16)

Why this exists: OPS is the only role that can read the cross-tenant audit
chain, so OPS membership must stay small, attributable, and reversible. These
rules apply to every environment (on-premise and cloud); on-prem grants are
executed by the DBA, cloud grants by the OPS operator team.

**Principles**

- One personal OPS account per operator. No shared accounts, no reuse of tenant ADMIN accounts.
- OPS is break-glass, not a daily-driver: it is used for audit verification and emergency operations, not for routine work.
- Every grant, revoke, and handover is an auditable event (see Logging).
- Least privilege over convenience: when in doubt, do not grant.

**Grant**

1. Request in writing (deployment ticket or equivalent): who, which deployment, justification, expiry/review date.
2. Approval by a second member of the operator team (four-eyes). The requestor must not approve their own grant.
3. Verify the target account is dedicated to the operator and belongs to no organization (tenant `NULL`/unassigned per your bootstrap procedure; confirm with `SELECT tenant_id FROM users WHERE username = '<ops-username>'`).
4. Execute the SQL in this section; then verify: login succeeds, and `GET /v1/audit-logs/verify` returns `200` with `valid=true`.
5. Log the event (see Logging).

**Revoke**

Revoke immediately when any of these is true: the operator leaves the team or the company, the operator's role changes so OPS is no longer needed, or compromise/misuse is suspected (revocation never waits for investigation).

1. `DELETE FROM user_roles WHERE user_id = (SELECT id FROM users WHERE username = '<ops-username>') AND role_id = (SELECT id FROM roles WHERE name = 'OPS');`
2. Verify: the account now receives `403` on `GET /v1/audit-logs/verify`.
3. Log the event (see Logging). Note the reason code: `offboarded`, `role_change`, `suspected_compromise`, or `no_longer_needed`.
4. If `suspected_compromise`: also rotate any credential that operator held, and have another operator re-verify the audit chain for the exposure window.

**Handover & periodic review**

- On every operator departure: revoke the departing account (Revoke above) and grant the replacement before the departure is complete; never leave a deployment with zero OPS holders (see break-glass note).
- Quarterly review (recommended default, adjust per org): list current holders with `SELECT u.username FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id WHERE r.name = 'OPS';` and confirm each still needs access, still belongs to the operator team, and the account is still dedicated. Record the review result in the change log.
- The review owner is the operator team lead; if unspecified, Boss holds this duty until delegated in writing.

**Logging**

Grant, revoke, and review events must record: date, environment, operator (subject), approver, action (`grant`/`revoke`/`review`), reason, and verification result. Where: the deployment change log (`CHANGELOG.md` entry or a deployment ticket) — not only verbal confirmation.

**Open items for Boss**

- Named members of the OPS operator team per deployment and the approver chain (four-eyes) — org-specific, not in this repo.
- Whether the quarterly review cadence above is adopted as-is or adjusted.

**Metrics for alerting (v1.1.0):** `iam.security.rejected{reason=unauthorized,method}` (401) with per-minute aggregate rows in `security_rejection_stats.reason`: `missing_credentials`, `invalid_token`, `tenant_mismatch`, `missing_organization`, `unknown_organization`; plus `access_denied` for 403 (audited as `ACCESS_DENIED`).

---

## 14. TLS Termination (v1.1.0, 2026-11-16)

HTTPS for the LAN deployment, terminated in the application itself (no reverse proxy). All statements verified live on 2026-11-16.

**Design**
- PKCS12 keystore issued by the internal **Osprey Internal CA** (`CN=Osprey Internal CA, O=Osprey Internal, C=ID`, 10-year CA valid until 2036). Server certificate: `CN=iam.osprey.service`, `O=Osprey Internal, C=ID`, SAN=`DNS:iam.osprey.service`, 825 days (2026-11-16 → 2029-02-18). Chain verified with `openssl verify -CAfile osprey-ca.crt` → OK.
- Key material lives in `E:\prod\iam-deploy\certs\` (ACL: Boss-only, mirroring `secrets\`): `osprey-ca.key`, `osprey-ca.crt`, `iam.osprey.service.key`, `iam.osprey.service.crt`, `iam.osprey.service.p12`. `osprey-ca.crt` (public certificate) is distributed to clients that need to trust the CA; the key files are never copied off the host.
- Tomcat serves HTTPS only on 9090. Plain HTTP requests return 400 (not servable). There is no HTTP→HTTPS redirect (no plain connector exists).
- TLS config is injected via env vars from `secrets\.env` (gitignored, host-only) — **Spring canonical env-var names** (dashes removed): `SERVER_SSL_ENABLED`, `SERVER_SSL_KEYSTORE`, `SERVER_SSL_KEYSTORETYPE`, `SERVER_SSL_KEYALIAS`, `SERVER_SSL_KEYSTOREPASSWORD`. The keystore is mounted read-only at `/certs/iam.osprey.service.p12` in the container.
- Rollback: remove the five `SERVER_SSL_*` lines from `.env`, remove the `certs` volume mount from `docker-compose.prod.yml`, recreate the container → service returns to HTTP v1.1.0 behavior. Backups for both files: `secrets\.env.bak-2026-11-16-pre-tls`, `app\docker-compose.prod.yml.bak-2026-11-16-pre-tls`.

**Client requirements**
- DNS: `iam.osprey.service` must resolve to `192.168.18.250`. **Action (Boss): add a DNS override / host record on the LAN gateway or DNS server (`192.168.18.250 -> iam.osprey.service`).** Until then, every client needs a `hosts` entry (`192.168.18.250 iam.osprey.service`) or `--resolve`-style pinning.
- Trust: `osprey-ca.crt` must be imported into the client trust store once (Windows: open the `.crt` → Install → Local Machine → Trusted Root Certification Authorities; Java: `keytool -importcert`; curl: `--cacert osprey-ca.crt`).
- **Windows schannel revocation caveat:** the internal CA publishes no CRL/CDP, so Windows schannel clients (e.g. `curl.exe` on Windows) fail with `schannel: the revocation status is unknown` unless invoked with `--ssl-revoke-best-effort` (or revocation checking disabled for this host). Browsers (Chrome/Edge) and API-to-API stacks with non-enforcing defaults (Java, .NET) are unaffected. Mitigation if this becomes a real problem: publish an internal CRL (CDP in a reissued cert + a small HTTP host for the CRL), or move TLS material/config to a managed reverse proxy.
- CORS: `CORS_ALLOWED_ORIGINS` in `secrets\.env` still reflects browser origins — updated client origins must use `https://iam.osprey.service:9090`; verify CORS after client migration.

**Operational notes**
- Cert expiry: server cert 2029-02-18, CA 2036. Calendar a renewal before expiry; renewal = issue CSR from existing key or new key, sign with CA, re-export PKCS12, update `SERVER_SSL_KEYSTOREPASSWORD`, recreate container (same procedure as 2026-11-16).
- `/actuator/prometheus` is unchanged: served over HTTPS now, still `permitAll` (see §9 SEC-02).
- `/v3/api-docs` remains 404 (Swagger disabled in prod).
