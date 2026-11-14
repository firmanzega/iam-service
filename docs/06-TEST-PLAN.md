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

No open bugs from Phase 6 execution.

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

### 10.2 Known issues NOT fixed (awaiting Boss decision)

| ID | Defect | Evidence |
|----|--------|----------|
| BUG-TENANT-001 | Unknown/invalid organization header silently falls back to org-default instead of being rejected | ADR-0016 Verification says invalid organization returns 401/404; `TenantResolver.resolve()` returns void with empty context, services fall back to org-default; `/v1/audit-logs` returns default-tenant logs to a client presenting an unknown org |
| BUG-TENANT-004 | Login user lookup does not enforce tenant isolation despite documentation claiming it does; generated SQL is `where username=? or email=?` with no tenant clause | `SpringDataUserRepository.findByUsernameOrEmail` comment claims tenant filter; log shows no tenant predicate; identity ambiguous across tenants (two users named `admin` in different tenants are indistinguishable at login) |

### 10.3 New / updated tests

| Test | Result | Proves |
|------|--------|--------|
| `TenantResolverTest` (5 cases) | LULUS | Context cleared on blank/null header, missing organization, inactive tenant; set when organization active |
| `TenantIsolationIntegrationTest.sessions_withoutOrganizationHeader_returns400` | LULUS | Mandatory header gate (after BUG-TENANT-002 fix) |
| `TenantIsolationIntegrationTest.logout_withoutOrganizationHeader_returns400` | LULUS | Same for `/v1/auth/logout` |
| `TenantIsolationIntegrationTest.sessions_listsOnlyCallerTenantOwnSessions` | LULUS | Tenant A cannot see tenant B's sessions, and vice versa (IDOR) |
| `TenantIsolationIntegrationTest.revokeSession_fromOtherTenant_isNotFound_andOriginalStaysAlive` | LULUS | Cross-tenant revoke is 404; victim's refresh token still works |
| `TenantIsolationIntegrationTest.sessions_withUnknownOrganization_isRejected_notFallbackToDefaultTenant` | DISABLED (`BUG-TENANT-001`) | Expected contract test for known issue; activated once Boss decides correct status code |

Two legacy tests were updated to satisfy the new mandatory header contract
(`AuthSecurityIntegrationTest.logout_revokesTheRefreshToken`,
`SessionManagementIntegrationTest.listSessions_withoutToken_isUnauthorized`).
No assertions were weakened; both now send the now-mandatory `X-Organization-ID`
header (`org-default` is the bootstrap tenant).

**Latest run:** `Tests run: 249, Failures: 0, Errors: 0, Skipped: 1` — BUILD SUCCESS.

### 10.4 Updated release readiness

**Recommendation: GO with conditions.** Package A scope unchanged; for any
multi-tenant production use, BUG-TENANT-001 and BUG-TENANT-004 must be resolved
(contract decision) before onboarding a second organization. BUG-TENANT-002/003
are fixed and verified.

