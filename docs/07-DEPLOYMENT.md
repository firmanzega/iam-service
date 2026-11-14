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

- [ ] `SPRING_PROFILES_ACTIVE=prod` set
- [ ] `JWT_SECRET` replaced with random value, minimum 32 bytes, not in any file on server
- [ ] `JWT_SECRET_PREVIOUS` empty, except during key rotation transition
- [ ] `POSTGRES_PASSWORD` changed, not in any file
- [ ] Database port not open to internet
- [ ] HTTPS active, certificate valid
- [ ] Production database does not contain default `admin` account from dev profile
- [ ] `CORS_ALLOWED_ORIGINS` filled with correct domain, and browser clients send `X-Organization-ID` (CORS policy allows the header)
- [ ] Clients updated to send `X-Organization-ID` on login/refresh and all tenant-aware endpoints (missing/unknown organization returns 401)
- [ ] Flyway migrations V9–V11 applied and validated (`spring.flyway.validate-on-migrate`)
- [ ] `/actuator/prometheus` only available on internal network, not via public gateway
- [ ] GitHub Actions green on last commit (mvn verify + Trivy + restore drill)
- [ ] Container image successfully built and run as non-root
- [ ] Scheduled database backup has been tested for recovery
- [ ] `iam.jwt.refresh-token-ttl` reviewed (also device session inactive threshold)
- [ ] `iam.security-rejection-stats.retention` matches local retention policy
- [ ] Session and 401 aggregate cleanup enabled, or scheduled outside the application
- [ ] Tracing collector endpoint filled if tracing enabled, or sampling `0.0`

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
