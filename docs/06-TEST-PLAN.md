# Test Plan — IAM Service (Package A, Phase 1–6)

> English (technical documentation per `team-workflow` standard).
> Output of **Phase 6** (`senior-qa-engineer`).

---

## 1. Orientation

**Tested:** IAM Service backend-only (Spring Boot 4.1.2, Java 21, PostgreSQL 16).
**Package A coverage:** JWT authentication, RBAC, audit trail, per-device sessions, JWT key rotation, 401 rejection aggregate, Prometheus registry, Docker, restore drill CI.

**Oracle source:**
- `docs/01-PRD.md` — Acceptance Criteria dan user stories
- `docs/04-API-SPEC.yaml` — status code, payload schema, error catalog
- `docs/03-ARCHITECTURE.md` — architecture pattern and constraints
- `docs/08-CODEBASE-MAP.md` — code structure map

**Stack test yang ada:** JUnit 5, Mockito, MockMvc, Testcontainers PostgreSQL 16, JaCoCo, Checkstyle, SpotBugs, CycloneDX, Trivy.

**Environment:** Host Windows, Docker available for Testcontainers and image build. `mvn clean verify` already run and passed (evidence in terminal execution history).

---

## 2. Risk & Ambiguity Analysis

### 2.1 Expected behavior (summary)

- Login generates access token (15 minutes) + refresh token (7 days), one session per device.
- Refresh token can only be used once (rotation). Old token used again = `REFRESH_TOKEN_REUSE`, revoke all user sessions.
- Logout revokes sent session token + all refresh tokens inside it. Other devices not affected.
- `DELETE /v1/auth/sessions/{id}` revokes one caller-owned session; other user's session 404.
- 401 added to `security_rejection_stats` per minute/method/reason, no path/IP.
- 403 written to `audit_logs` as `ACCESS_DENIED` with actor.
- Access token already issued not revoked instantly (maximum 15 minutes).

### 2.2 Ambiguity & requirement gaps

| # | Ambiguity / Gap | Impact | Status |
|---|-------------------|--------|--------|
| A-1 | Access token cannot be revoked instantly (maximum 15-minute window) | Revoked session still accessible until token expires | Identified, accepted (Package A limitation) |
| A-2 | 401 not written to audit_logs (avoid flood) | Cannot trace 401 actor in audit | Identified, intentionally skipped |
| A-3 | Rate limiter per instance, not distributed | Effective limit more loose in multi-instance | Identified, gateway mitigation |
| A-4 | `maven.java.net` unreachable in build network | Docker build fails without mirror settings | Fixed via `.mvn/settings.xml` |
| A-5 | Device label from client can be forged | Label is not identity, not used for revocation | Identified, clear documentation |

### 2.3 Risk list

| Risk | Probability | Impact | Level |
|--------|-------------|--------|---------|
| Token reuse not detected properly | Medium | High (token theft) | **High** |
| Other user session leak (IDOR) | Low | High (access exception) | **High** |
| 401 aggregate table bloats | Low | Medium (storage) | Medium |
| Docker build fails at CI (dead repository) | High | High (release blocked) | **High** (already fixed) |
| Coverage below 80% on new class | Medium | High (build fails) | Medium |

---

## 3. Scope & Out of Scope

**Tested:**
- Login, refresh, logout, list sessions, revoke one session (HTTP + real database)
- Authorization matrix (ADMIN, AUDITOR, USER, account owner)
- 401 aggregate upsert and retention cleanup
- Prometheus scrape without token, metric names match alert rules
- JWT key rotation with `kid`, previous key accepted
- Backup/restore round-trip (CI restore drill)

**Intentionally not tested (Package A limitations):**
- Distributed rate limit (none)
- Direct access token revocation (none)
- WORM / external audit append-only (none)
- SAML/OIDC, multi-tenancy, self-registration (none)
- Front-end/UI (backend-only)

---

## 4. Test Case List (priority based on risk)

| ID | Title | Type | Reference | Status |
|----|-------|------|-----------|--------|
| QA-001 | Login generates access + refresh token, creates per-device session | Positive | PRD 3.1, 3.3 | Passed (SessionManagementIntegrationTest) |
| QA-002 | Refresh token rotation: old token revoked, new token issued | Positive | PRD 3.1 | Passed (RefreshTokenReuseIntegrationTest) |
| QA-003 | Token reuse (revoked but session still alive) → `REFRESH_TOKEN_REUSE`, revoke all sessions | Negative | PRD 3.1 | Passed (RefreshTokenReuseIntegrationTest) |
| QA-004 | Logout revokes entire session token, other devices not affected | Positive | PRD 3.3 | Passed (SessionManagementIntegrationTest) |
| QA-005 | `DELETE /v1/auth/sessions/{id}` belongs to someone else → 404, same as non-existent | Negative | PRD 3.3 | Passed (SessionManagementIntegrationTest) |
| QA-006 | `GET /v1/auth/sessions` only belongs to caller, `current` flag | Positive | PRD 3.3 | Passed (SessionManagementIntegrationTest) |
| QA-007 | 401 aggregate per minute/method/reason, no path/IP | Positive | PRD 3.5 | Passed (AuthSecurityIntegrationTest) |
| QA-008 | Prometheus scrape 200 without token, metrics match alerts.yml | Positive | PRD 3.6 | Passed (PrometheusEndpointIntegrationTest) |
| QA-009 | JWT key rotation with `kid`, previous key accepted during transition | Positive | PRD 3.1 | Passed (JwtKeyRotationIntegrationTest) |
| QA-010 | JWT without `kid` accepted when previous key configured | Positive | PRD 3.1 | Passed (JwtKeyRotationIntegrationTest) |
| QA-011 | Backup/restore round-trip: rows, indexes, unique constraints safe | Integration | PRD 3.7 | Passed (restore-drill.yml CI) |
| QA-012 | Coverage ≥ 80% per class, Checkstyle 0, SpotBugs Medium passed | Gate | PRD 5 | Passed (mvn clean verify) |
| QA-013 | Docker image build successful, non-root user, port 9090 | Operational | PRD 3.7 | Passed (docker build 598MB) |

---

## 5. Execution Results (terminal evidence)

> Historical baseline from Package A phases. See §10 for the 2026-11-14 retest,
> which records `mvn clean verify` failing the per-class JaCoCo gate (QA-GATE-001)
> for the multi-tenancy increment; the "BUILD SUCCESS" rows above predate `63814ac`.

| Command | Result | Evidence |
|----------|-------|-------|
| `mvn clean verify` | **BUILD SUCCESS** | `f6-verify.log`: All coverage checks met, 0 Checkstyle violations, SpotBugs passed |
| `mvn test -Dtest=PrometheusEndpointIntegrationTest` | **2/2 passed** | `f5-prometheus2.log`: BUILD SUCCESS |
| `mvn test` (Phase 4) | **237 tests passed** | `f4-verify3.log`: BUILD SUCCESS |
| Phase 4 integration tests | **45/45 passed** | `f4-it.log`: BUILD SUCCESS |
| `docker build --tag iam-service:ci` | **Successful** | Image 598MB |

**No failed tests.** All quality gates met.

---

## 6. Open Bugs

- **QA-GATE-001: FIXED 2026-11-14** — `mvn clean verify` is now BUILD SUCCESS (299/299, JaCoCo per-class met, Checkstyle 0, SpotBugs 0). Backend removed the dead `RejectionStatRow` (existence-only `SELECT 1` → `Integer`).
- **SEC-STATS-001 (severity Medium): FIXED + QA-verified 2026-11-14 (Option A, Boss-approved):** `V11__tenant_scoped_rejection_stats_unique.sql` drops V8's global unique and adds composite `uq_security_rejection_stats_tenant UNIQUE (tenant_id, window_start, http_method, reason)`; `increment()` is a single-statement `INSERT ... ON CONFLICT DO UPDATE`. QA re-verified cross-tenant same-minute 401 counting: `TenantIsolationIntegrationTest.unauthorized_aggregatesPerTenant_sameMinuteSameMethodAndReason` passes (12/12 in class), and the test was proven falsifiable by temporarily reverting the upsert (expected <2> but was <1>) and restoring afterwards. Item closed.
- **BUG-TENANT-001 and BUG-TENANT-004:** fixed and verified in Stage 4 + Phase 6 retest (see §10.2).

---

## 7. Residual Risks

| Risk | Level | Note |
|--------|---------|---------|
| Access token 15-minute window not revoked instantly | Medium | Accepted as Package A limitation (A-1) |
| Rate limit per instance, not distributed | Medium | Gateway mitigation (A-3) |
| `maven.java.net` unreachable in build network | Resolved | Fixed via `.mvn/settings.xml` mirror (A-4) |

---

## 8. Release Readiness

**Recommendation: GO** (with condition that residual risks are accepted).

Reasons:
- All automated tests pass (237+ unit + integration, 45 HTTP integration, 2 Prometheus)
- All quality gates met: JaCoCo ≥80% per class, Checkstyle 0, SpotBugs Medium passed
- SBOM CycloneDX 1.6 generated, Docker image build successful
- Automated restore test at CI (restore-drill.yml) available
- ADR 0014/0015 documents architecture decisions

Requirements for production release:
1. `JWT_SECRET` replaced with random value minimum 32 bytes, not on disk
2. `/actuator/prometheus` only reachable by internal scraper network (not public ingress)
3. `SPRING_PROFILES_ACTIVE=prod` set
4. Scheduled database backup and tested recovery

---

## 9. Self-Audit Anti-AI-Slop

- [x] No fabricated execution results; all status based on real terminal evidence
- [x] Every selector, endpoint, and metric verified against existing code/contract
- [x] Negative, boundary, and access scenarios exist (not just happy path)
- [x] No placeholder, TODO, or empty test
- [x] Facts separated from assumptions; residual risks stated explicitly
- [x] No claims of "safe/tested/release-ready" without basis and coverage boundary

---

## 10. Multi-Tenancy Verification — Phase 6 Retest (2026-11-14)

Phase 4 (commit `63814ac`) shipped V9 `tenant_id` migration and tenant-aware
repositories. QA retest against ADR-0016 (`Verification`) found two product
defects; both fixed in `8f125e8` and covered by new tests.

### 10.1 Bugs found and fixed

| ID | Defect | Evidence | Fix |
|----|--------|----------|-----|
| BUG-TENANT-002 | `TenantHeaderFilter` matched `request.getServletPath()`, which is empty under MockMvc and fragile in general, so tenant-required endpoints never rejected requests without `X-Organization-ID` | probe test printed `servletPath=[] pathInfo=[/v1/auth/sessions]`; `/v1/auth/sessions` and `/v1/auth/logout` returned 200/204 without header (contract: 400) | filter now resolves a path via requestURI/pathInfo fallback and strips context path |
| BUG-TENANT-003 | `Session`/`RefreshToken` rows created at login were attributed to the request-context tenant (org-default fallback), not the owning user's tenant; tenant-B sessions never listed/usable | `TenantIsolationIntegrationTest.sessions_listsOnlyCallerTenantOwnSessions` failed with B seeing 0 sessions | session and refresh-token attribution uses `user.getTenantId()` |

### 10.2 Retest of BUG-TENANT-001 and BUG-TENANT-004 (2026-11-14, after Stage 4 commits `d16d692`, `f57ca24`)

Both defects were fixed in Stage 4; retested here against the locked contract
(ADR-0018; Boss rulings 2026-11-14: unknown organization is rejected with 401 on
every path, and missing `X-Organization-ID` is 401 on every tenant-aware path —
unified on 2026-11-14; error body carries missing-vs-unknown cause).

| ID | Retest result | Evidence |
|----|---------------|----------|
| BUG-TENANT-001 | **FIXED — verified** | `sessions_withUnknownOrganization_isRejectedWith401_notFallbackToDefaultTenant` (previously `@Disabled`) now runs and passes; new tests `login_withoutOrganizationHeader_returns401`, `login_withUnknownOrganization_returns401`, `refresh_withoutOrganizationHeader_returns401`, `refresh_withUnknownOrganization_returns401` all pass; `TenantIsolationIntegrationTest` (11/11) |
| BUG-TENANT-004 | **FIXED — verified** | Root cause in the derived query `findByUsernameOrEmail` (tenant parameter ignored entirely; `WHERE username=? OR email=?`) gone: explicit `@Query` with mandatory tenant predicate (ADR-0018 §3); `login_sameIdentityInTwoTenants_bothSucceed` registers+logs in the same identity in `org-default` and `org-qa-isolation`; `V10__tenant_scoped_identity.sql` adds `UNIQUE(tenant_id, username)` / `UNIQUE(tenant_id, email)` |

Additional Stage 4 coverage added by QA this phase (44 tests, 257 → 301 total):

- `LoggingServiceImplTest`, `LogMessageTest`, `LogEntryTest`, `LoggingAdapterTest` — the
  ADR-0017 structured-logging slice was previously at 0–19% per class; now covered,
  including MDC leak checks (no cross-request tenant/trace leak).
- `TenantIsolationIntegrationTest.unauthorized_aggregatesPerTenant_sameMinuteSameMethodAndReason`
  (2026-11-14) — proves SEC-STATS-001 fixed: two tenants increment the same minute/method/reason
  aggregate independently; fixture now also deletes `security_rejection_stats` rows for the QA
  tenant before tenant teardown (FK `fk_security_rejection_stats_tenant_id` is enforced).
- `TenantTest`, `TenantPersistenceAdapterTest`, `PersistenceEntityTest.tenantJpaEntity_*`,
  `SecurityRejectionStatPersistenceAdapterTest`, `SecurityRejectionStatsServiceTest`
  (default-tenant fallback paths), `AuditLogTest`, `WebMapperTest.tenantId_*` —
  closes the per-class 80% gate gap that had been red since `63814ac`.
- `AuthServiceTest.refresh_prefersStoredTokenTenant_overCurrentContext` and
  `AuthServiceTest.login_withoutTenantContext_throwsIllegalState` pin ADR-0018 point 6
  and the fail-closed filter contract.

### 10.3 Known issues NOT fixed (escalated to `senior-backend-developer`, 2026-11-14)

| ID | Defect | Evidence |
|----|--------|----------|
| QA-GATE-001 | ~~FIXED 2026-11-14~~ `mvn clean verify` failed the JaCoCo per-CLASS LINE 0.80 gate on `infrastructure.persistence.SecurityRejectionStatPersistenceAdapter.RejectionStatRow` (ratio 0.66). Root cause was production code: `increment()` only checked row existence and never read the row's values. | `jacoco.csv` before fix; fixed by mapping `SELECT 1` to `Integer` (class deleted entirely). `mvn clean verify` is now **BUILD SUCCESS** (299/299, Checkstyle 0, SpotBugs 0). The class of failure could not have been introduced by test changes. |

Adjacent defect found during the fix (escalated, NOT fixed here): the
`security_rejection_stats` table still carries V8's global
`uq_security_rejection_stats UNIQUE (window_start, http_method, reason)` with no
`tenant_id`. Two tenants hitting 401 in the same minute with the same method and
reason → the second tenant's INSERT violates the global unique → the recorder swallows
it (`persistRejectionStat` swallows the exception and only writes an ERROR log) →
that tenant's 401 aggregate is silently lost. Fix would be a V11 migration
(drop global unique, add `UNIQUE(tenant_id, window_start, http_method, reason)`) —
schema change to a core table, escalated per `team-workflow` Role Lock to
`solution-architect`/Boss; not done unilaterally. Related: `docs/08-CODEBASE-MAP.md`
claims the adapter uses `ON CONFLICT upsert`; the implementation is SELECT-then-
INSERT/UPDATE (docs drift, minor).

Note: `mvn clean verify` had already been red since `63814ac` (the multi-tenancy backend
commit) for the logging/tenant/WebMapper/AuditLog/SecurityRejectionStatsService classes;
this stage brought every other class above the gate. The Package A "verify passed" claim in
§5 below refers to pre-multi-tenancy history and is stale for the current HEAD.

Two legacy tests were updated to satisfy the new mandatory header contract
(`AuthSecurityIntegrationTest.logout_revokesTheRefreshToken`,
`SessionManagementIntegrationTest.listSessions_withoutToken_isUnauthorized`).
No assertions were weakened; both now send the now-mandatory `X-Organization-ID`
header (`org-default` is the bootstrap tenant). The two
`*_withoutOrganizationHeader_returns400` tests were renamed to `*_returns401` when
Boss unified missing-header rejection to 401 on 2026-11-14.

**Latest run:** `Tests run: 299, Failures: 0, Errors: 0, Skipped: 0` — `mvn clean verify`
**BUILD SUCCESS** (JaCoCo per-class gate met, Checkstyle 0 violations, SpotBugs 0).
Flyway applies V11 on the Testcontainers PostgreSQL 16 in every IT run, and
`unauthorized_aggregatesPerTenant_sameMinuteSameMethodAndReason` proves both tenants
increment the aggregate in the same minute for the same method and reason
(`expected <2> but was <1>` when the upsert is reverted). SEC-STATS-001 is closed.

### 10.4 Updated release readiness

**Recommendation: GO with conditions.**

Conditions:
1. ~~QA-GATE-001~~ resolved 2026-11-14 (`mvn clean verify` BUILD SUCCESS). Remaining condition: BUG-TENANT-001 and BUG-TENANT-004 are fixed and verified; Package A must not onboard a second organization until they stay green in CI.
2. ~~SEC-STATS-001~~ closed 2026-11-14: backend fix merged (V11 + upsert) and QA re-verified cross-tenant same-minute 401 counting in `TenantIsolationIntegrationTest` (test proven falsifiable against the pre-fix behavior). Severity Medium; observability defect, not an authorization defect.
3. Release version for the breaking contract (`1.1.0` vs `2.0.0`) — Boss decision pending.

BUG-TENANT-002/003 and both multi-tenancy defects are fixed and verified. The remaining
known risk is process-level: the multi-tenancy increment (`63814ac`) was merged without a
green `mvn clean verify`; gates should run on the full matrix before merge in Stage 7.

---

## 11. Role Catalog Read Restriction — Retest (2026-11-20)

Oracle: `04-API-SPEC.yaml` `GET /roles` (ADMIN/AUDITOR/OPS only) and the `SecurityConfig`
matcher. The `memberToken` fixture in `AuthSecurityIntegrationTest` is an account with
**no roles**. Decision: opsi B from the SA analysis — OPS-only would have broken tenant
ADMIN's role-assignment flow.

### 11.1 Test cases

| ID | Title | Type | Reference | Expected | Status |
|----|-------|------|-----------|----------|--------|
| QA-014 | Account with no roles reads the global role catalog — `listRoles_asMember_isForbidden` | Negative / access control | 04-API-SPEC GET /roles | 403 | Passed |
| QA-015 | Tenant ADMIN keeps read access needed for role assignment — `listRoles_asAdmin_isAllowed` | Positive | 04-API-SPEC GET /roles | 200 + array | Passed |
| QA-016 | AUDITOR keeps read access — `listRoles_asAuditor_isAllowed` | Positive | 04-API-SPEC GET /roles | 200 + array | Passed |
| QA-017 | OPS keeps read access — `listRoles_asOps_isAllowed` | Positive | 04-API-SPEC GET /roles | 200 + array | Passed |
| QA-018 | Role master stays readable without `X-Organization-ID` — `roles_withoutOrganizationHeader_remainReadable_globalReferenceData` | Positive / tenancy | V9 global role table | 200 with ADMIN token | Passed (fixture corrected) |

### 11.2 Stale test found and corrected

`TenantIsolationIntegrationTest.roles_withoutOrganizationHeader_remainReadable_globalReferenceData`
used `PREFIX + "a"`, an account with **no roles**, to prove the endpoint does not require
`X-Organization-ID`. That fixture encoded the superseded contract (any authenticated user).
The assertion was **not** weakened: the fixture now uses `loginAdmin(...)` with an ADMIN
role, so the test still verifies exactly its original intent (no tenant header needed),
while role authorization is asserted separately in QA-014..017.

### 11.3 Execution evidence

| Command | Result |
|---------|--------|
| `mvnw -B -Dtest=AuthSecurityIntegrationTest test` | EXIT=0, 38/38 passed |
| `mvnw -B clean verify` (first run) | EXIT=1, 318 tests / 1 failure — QA-018 stale fixture (`Status expected:<200> but was:<403>`) |
| Mutation check: matcher temporarily reverted to `authenticated()`, `AuthSecurityIntegrationTest#listRoles_asMember_isForbidden` | EXIT=1, `Status expected:<403> but was:<200>`; `SecurityConfig` restored in `finally`, diff reverified |
| `mvnw -B clean verify` (after fixture correction) | EXIT=0, 318/318 passed, 0 Checkstyle violations, SpotBugs passed |

### 11.4 Scope boundary and residual risk

Not covered: browser/client consumers of `GET /v1/roles` (no frontend in this repo). Any
client relying on USER/MANAGER reading the catalog now gets 403 — a contract change
announced in `CHANGELOG.md` 1.1.3. No load test was run.

### 11.5 Production smoke (owner-executed, 2026-11-20)

After `iam-service:1.1.3` was deployed by the owner, `smoke-1.1.3.ps1` was run against
production:

| Check | Status |
|-------|--------|
| `/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |
| `GET /v1/roles` without token | **401** |
| `GET /v1/roles` as admin (roles `[ADMIN]`) | **200** |
| `GET /v1/roles` as ops (roles `[OPS]`) | **200** |
| `GET /v1/roles` as throwaway no-role user | **403** |

The throwaway account (id 4) was hard-deleted in a single transaction (1 refresh token,
1 session, 1 user); 5 audit log rows referencing it were deliberately kept so the hash
chain stays intact — `audit_logs` has no FK to `users`, so the actor id remains a
historical reference. Verified: `remaining user4=0`. This is production evidence for
QA-014..017 in addition to the CI results in §11.3.

---

## 12. Stage 7 Close-Out — Release Readiness (2026-11-20)

**Recommendation: GO with accepted residuals.** Boss attested Stage 7 closure on
2026-11-20.

Evidence base:

| Area | Result |
|------|--------|
| Automated suite | `mvn clean verify` EXIT=0, 318/318 tests, 0 Checkstyle, SpotBugs passed |
| Production | `iam-service:1.1.3` running, bytecode marker confirmed, health `UP` |
| Authorization live | `GET /v1/roles`: anon 401, admin 200, ops 200, no-role 403 |
| Earlier releases | 1.1.1-hotfix and 1.1.2 smoke-passed and recorded in `07-DEPLOYMENT.md` §6 |
| Release artifacts | Rollback images retained; no DB migration in 1.1.3 |

Accepted residuals (Boss decision, not defects found in this increment):

| ID | Residual | Impact |
|----|----------|--------|
| SEC-01 | DB port 5433 published on all host interfaces, password auth | Internal-only deployment; closure plan documented |
| SEC-02 | `/actuator/prometheus` anonymously reachable on internal network | Internal scraper only |
| CRED-01 | DB password value appeared in the session transcript; rotation declined | Accepted by Boss; procedure documented in final handoff |
| DOC-01 | 3 pre-existing cspell word flags in ADR-0020/0021 | Documentation-only, outside this increment |
| PERF-01 | No load/soak test has been executed | Soak v1.1.3 continues under Boss observation |

Not covered by this close-out: browser/client consumers of the `GET /v1/roles`
contract change, and any performance claim. No claim of "no remaining bugs" is made.

---

## 13. Phase 1 Tenant Provisioning (CR-2026-11-20)

### 13.1 Oracle & risk

Oracle: `docs/01-PRD.md` §5 acceptance criteria, `docs/04-API-SPEC.yaml`
`/tenants` paths, ADR-0020 and ADR-0023. The highest risk is not a missing
tenant row but a **privilege or idempotency defect**: a tenant ADMIN
provisioning a tenant, a replay creating a duplicate, or the first ADMIN
landing in the operator's tenant instead of the new one. A second risk is the
audit hash-chain lock: an independent audit transaction written while the
outer transaction already holds an audit-row lock can hang until lock timeout.

### 13.2 Automated cases

| ID | Case | Expected | Type | Result |
|----|------|----------|------|--------|
| TC-TEN-01 | `POST /v1/tenants` as member (`USER` only) | `403`; no tenant row created | Negative / access control | Passed |
| TC-TEN-02 | `POST /v1/tenants` as OPS, replay same payload | `201` then `200`; response has `organizationId`, `name`, `active=true` | Positive + idempotency | Passed |
| TC-TEN-03 | Replay with same `organizationId`, different `name` | `409` | Boundary / conflict | Passed |
| TC-TEN-04 | Bootstrap before tenant exists | `404` | Negative | Passed |
| TC-TEN-05 | Bootstrap as OPS, then replay | `201` then `200`; role contains `ADMIN`; one admin only | Positive + idempotency | Passed |
| TC-TEN-06 | New ADMIN logs in with the new `X-Organization-ID`; `GET /v1/tenants/current` | `200` with the new `organizationId` | Cross-tenant correctness | Passed |
| TC-TEN-07 | `TENANT_PROVISIONED` rows carry actor and `organization_id` in `details` | At least one row; `actor_user_id` not null | Audit side effect | Passed |

Implementation: `src/test/java/id/osprey/iamservice/adapter/rest/TenantProvisioningIntegrationTest.java`
(MockMvc + Testcontainers PostgreSQL 16). Data is isolated by a random
`provtest-` prefix and removed in `@AfterEach`.

### 13.3 Execution evidence

Command: `./mvnw -Dtest=TenantProvisioningIntegrationTest test`
Result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
The four test methods cover the seven cases above (idempotency and conflict
are asserted inside one method each).

Full SIT gate: `./mvnw clean verify` -> `Tests run: 322, Failures: 0,
Errors: 0, Skipped: 0`, 0 Checkstyle violations, SpotBugs 0 bugs, JaCoCo
coverage checks met, CycloneDX 1.6 SBOM produced, `BUILD SUCCESS`.

### 13.4 Mutation / failure proof

Before the fix, the full class **did not finish within 600 seconds**: the
bootstrap test hung after `userService.createInTenant` wrote `USER_CREATED` and
`ROLES_REPLACED`, then `recordIndependently` opened a `REQUIRES_NEW` transaction
that waited for the audit-row lock held by its own suspended parent
transaction. After changing bootstrap audit to `record()` (same transaction),
the same class finished in about 24 seconds with 4/4 passing. This is evidence
that the suite detects the defect, not a permanently green test.

### 13.5 Scope & residual risk

Not covered: concurrent double-submit of provisioning (a database unique
constraint still prevents duplicates, but the loser receives `409` rather than
`200`), performance under load, and the HRIS consumer (excluded by Boss).
No claim is made beyond the cases above.

---

## 14. Structured Logging & Request Correlation (CR-STRUCTLOG-001)

### 14.1 Oracle & risk

Oracle: ADR-0024, `03-ARCHITECTURE.md` §7, and the acceptance criteria composed
in chat for CR-STRUCTLOG-001. The highest risk is **MDC leakage**: a request id
or tenant id left in a thread-local map could be attached to an unrelated later
request on the same Tomcat thread. The second risk is a rejection path skipping
cleanup because it returns before the normal chain.

### 14.2 Automated cases

| ID | Case | Expected | Type | Result |
|----|------|----------|------|--------|
| SL-01 | `LoggingAdapter.write(ERRORS, ...)` | Level `ERROR`; MDC contains `tenantId`, `traceId`, and `logCategory=ERRORS` | Positive | Passed |
| SL-02 | Four non-error categories | All logged at `INFO` | Regression | Passed |
| SL-03 | Entry without `tenantId` after a prior MDC value | Event has no `tenantId`; previous value restored | Negative / isolation | Passed |
| SL-04 | Previous `tenantId`/`traceId` after write | Both restored | State | Passed |
| SL-05 | No previous MDC values after write | All three keys null | State | Passed |
| SL-06 | Entry without `traceId` after a prior value | Event has no `traceId`; previous restored | Negative / isolation | Passed |
| SL-07 | Previous `logCategory` after write | Restored | State | Passed |
| SL-08 | Resolved tenant request through `TenantHeaderFilter` | `requestId` and `tenantId="11"` present inside the chain; both null after | Positive / MDC cleanup | Passed |
| SL-09 | Missing header on `/v1/users` | Chain skipped; `recordUnauthorized(..., "missing_organization")` called; `requestId` still cleared | Negative / rejection path | Passed |

Implementation: `LoggingAdapterTest` (7 tests) and new
`TenantHeaderFilterMdcTest` (2 tests).

### 14.3 Execution evidence

`./mvnw clean verify` -> `Tests run: 325, Failures: 0, Errors: 0,
Skipped: 0`; 0 Checkstyle violations; SpotBugs 0 bugs; JaCoCo checks met;
CycloneDX 1.6 SBOM produced; `BUILD SUCCESS`.

### 14.4 Mutation / failure proof

Removing `MDC.remove(MDC_REQUEST_ID)` from `TenantHeaderFilter`'s `finally`
block made both `TenantHeaderFilterMdcTest` cases fail with:

```text
requestId must not leak after the request ==> expected: <null> but was: <uuid>
requestId must be cleared on the rejection path ==> expected: <null> but was: <uuid>
```

The line was restored and the targeted suite was green again. This proves the
new tests detect the leak rather than being permanently green.

### 14.5 Scope & residual risk

Not covered: runtime stdout inspection in a deployed container (to be done by
the Owner after deploy), log aggregator ingestion (Loki is out of scope), and
tracing spans (sampling remains `0.0`). `traceId`/`spanId` are expected to be
empty in the current configuration; `requestId` is the available correlation
value.

