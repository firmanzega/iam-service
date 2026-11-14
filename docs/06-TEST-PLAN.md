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
