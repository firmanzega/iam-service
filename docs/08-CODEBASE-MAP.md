# Codebase Map

> English (technical documentation per `team-workflow` standard).

## 1. Overview

```
src/main/java/id/osprey/iamservice/
├── IamServiceApplication.java      entry point, @ConfigurationPropertiesScan, @EnableScheduling
├── domain/                         pure business rules (no framework imports)
├── application/                    use case orchestration
├── infrastructure/                 adapters to framework and database
├── adapter/rest/                   HTTP layer
└── config/                         framework configuration
```

Dependency direction: `adapter` → `application` → `domain`. Reverse dependency is forbidden and enforced by `ArchitectureLayerTest`.

## 2. Domain Layer

| File | Role |
|------|------|
| `domain/auth/Session.java` | One device session. Stable ID, optional device label, last seen, revoked state |
| `domain/auth/RefreshToken.java` | Refresh token with revocation state and session owner |
| `domain/auth/SessionRepository.java` | **Port** for session storage, active session lookup, bulk revocation, cleanup |
| `domain/auth/RefreshTokenRepository.java` | **Port** for refresh token storage |
| `domain/auth/TokenIssuer.java` | **Port** for token issuance and hash calculation |
| `domain/security/SecurityRejectionStatRepository.java` | **Port** for 401 rejection aggregate per minute, plus retention cleanup |
| `domain/user/User.java` | User entity |
| `domain/user/Role.java` | Role entity |
| `domain/user/UserRepository.java` | **Port** for user storage |
| `domain/user/RoleRepository.java` | **Port** for role storage |
| `domain/shared/PaginationRequest.java` | Pagination request value object |
| `domain/shared/SortSpec.java` | Sort direction value object |
| `domain/shared/DomainException.java` | Domain exception with category, mapped to HTTP status in adapter |

## 3. Application Layer

| File | Role |
|------|------|
| `application/auth/AuthService.java` | Use case: login with device session, refresh, logout one session, list sessions, revoke one session, revoke all tokens. Implements `TokenRevocationService` |
| `application/auth/SessionCleanupService.java` | Deletes revoked or inactive sessions (older than refresh token TTL) |
| `application/auth/RefreshTokenCleanupService.java` | Deletes expired refresh tokens |
| `application/auth/RefreshTokenReuseService.java` | Revokes all sessions and logs `REFRESH_TOKEN_REUSE` in separate transaction when revoked token is reused |
| `application/auth/SessionSummary.java` | Session summary for device list, including caller's session flag |
| `application/auth/LoginAttemptService.java` | Records login failures in separate transaction |
| `application/auth/TokenPair.java` | Login result: access + refresh token pair |
| `application/role/RoleService.java` | Use case: list, create, delete roles |
| `application/audit/AuditService.java` | Audit logging. `record` joins transaction, `recordIndependently` uses separate transaction, `findAll` reads paginated audit log |
| `application/security/PasswordPolicy.java` | Password strength rules |
| `application/security/SecurityRejectionStatsService.java` | Records 401 rejection aggregate per minute in separate transaction, deletes aggregates past retention |
| `application/tenant/TenantResolver.java` | Resolves `X-Organization-ID` to internal `tenant_id`, sets `TenantContext`; returns `RESOLVED`/`MISSING`/`UNKNOWN` so the filter can reject unknown organizations (BUG-TENANT-001) |

## 4. Infrastructure Layer

| File | Role |
|------|------|
| `infrastructure/persistence/SessionJpaEntity.java` | JPA entity for `sessions` table |
| `infrastructure/persistence/RefreshTokenJpaEntity.java` | JPA entity for `refresh_tokens` table, including `session_id` column |
| `infrastructure/persistence/AuditLogJpaEntity.java` | JPA entity for `audit_logs` table, including hash column written by database trigger |
| `infrastructure/persistence/SpringDataUserRepository.java` | Spring Data repository for users; `findByUsernameOrEmail` is an explicit `@Query` with a mandatory `tenantId` predicate (derived query silently ignored the tenant parameter) |
| `infrastructure/persistence/SpringDataRoleRepository.java` | Spring Data repository for roles |
| `infrastructure/persistence/SpringDataRefreshTokenRepository.java` | Spring Data repository for refresh tokens, including bulk delete expired and bulk revoke via single `UPDATE` |
| `infrastructure/persistence/SpringDataSessionRepository.java` | Spring Data repository for sessions, including active session lookup, bulk revocation, delete inactive sessions |
| `infrastructure/persistence/SpringDataAuditLogRepository.java` | Spring Data repository for audit logs |
| `infrastructure/persistence/UserPersistenceAdapter.java` | Implements `UserRepository` |
| `infrastructure/persistence/RolePersistenceAdapter.java` | Implements `RoleRepository` |
| `infrastructure/persistence/RefreshTokenPersistenceAdapter.java` | Implements `RefreshTokenRepository` |
| `infrastructure/persistence/SessionPersistenceAdapter.java` | Implements `SessionRepository` |
| `infrastructure/persistence/SecurityRejectionStatPersistenceAdapter.java` | Implements `SecurityRejectionStatRepository` via `JdbcTemplate` and a single-statement `ON CONFLICT` upsert (arbiter: `UNIQUE(tenant_id, window_start, http_method, reason)`, V11) |
| `infrastructure/persistence/AuditLogPersistenceAdapter.java` | Implements `AuditLogRepository`, including full read for hash chain verification |

## 5. REST Adapter Layer

| File | Role |
|------|------|
| `adapter/rest/AuthController.java` | Endpoints: login, refresh, logout, list sessions, revoke one session |
| `adapter/rest/RoleController.java` | Endpoints: roles |
| `adapter/rest/AuditController.java` | Endpoints: audit logs, hash chain integrity check |
| `adapter/rest/WebMapper.java` | Shared web-to-domain translator: actor ID, session ID, pagination request |
| `adapter/rest/TenantHeaderFilter.java` | Enforces `X-Organization-ID` before the chain: missing or unknown organization → 401 on every tenant-aware path (ADR-0018, BUG-TENANT-001; unified 2026-11-14) |
| `adapter/rest/dto/SessionResponse.java` | Shape of one device session returned |
| `adapter/rest/dto/TokenResponse.java` | Shape of token pair returned |

## 6. Config Layer

| File | Role |
|------|------|
| `config/IamProperties.java` | `SessionCleanup`, `SecurityRejectionStats`, and all `iam.*` properties |
| `config/SecurityConfig.java` | Security filter chain, `/actuator/prometheus` permit, CORS |
| `config/RestAuthenticationEntryPoint.java` | 401 response, reason classification, WARN log + metric |
| `config/RestAccessDeniedHandler.java` | 403 response, audit `ACCESS_DENIED`, WARN log + metric |
| `config/SecurityEventRecorder.java` | Security event recording rules: 403 to audit, 401 to log + metric + aggregate |
| `config/JwtKeyRing.java` | JWT key ring with `kid`, current + previous key |
| `config/TokenClaims.java` | Shared token claim names, e.g. `session_id` |
| `config/SessionCleanupJob.java` | Scheduled cleanup of revoked/inactive sessions |
| `config/RefreshTokenCleanupJob.java` | Scheduled cleanup of expired refresh tokens |
| `config/SecurityRejectionStatsCleanupJob.java` | Scheduled cleanup of 401 aggregates past retention |

## 7. Database Migrations

| File | Role |
|------|------|
| `src/main/resources/db/migration/V1__create_schema.sql` | Initial schema |
| `src/main/resources/db/migration/V8__add_sessions_and_rejection_stats.sql` | `sessions` table and `security_rejection_stats` table |
| `src/main/resources/db/migration/V9__add_tenant_id.sql` | `tenant_id` on core tables, `tenants` registry, backfill to `org-default` |
| `src/main/resources/db/migration/V10__tenant_scoped_identity.sql` | Drops global `UNIQUE` on `users.username`/`users.email`, adds `UNIQUE(tenant_id, username)` and `UNIQUE(tenant_id, email)` (ADR-0018) |
| `src/main/resources/db/migration/V11__tenant_scoped_rejection_stats_unique.sql` | Drops the V8 global `uq_security_rejection_stats` (window_start, http_method, reason); adds `UNIQUE(tenant_id, window_start, http_method, reason)` (SEC-STATS-001) |

## 8. Tests

| File | Role |
|------|------|
| `adapter/rest/TenantIsolationIntegrationTest.java` | Cross-tenant isolation via HTTP: sessions per tenant, unknown-organization 401 (BUG-TENANT-001), required `X-Organization-ID` on login/refresh (ADR-0018), tenant-scoped identity uniqueness (V10) |
| `adapter/rest/AuthSecurityIntegrationTest.java` | Authorization matrix via HTTP, password change, account lock, logout, 401 aggregate |
| `adapter/rest/SessionManagementIntegrationTest.java` | Per-device sessions via HTTP: two logins, `current` flag, single-device logout, other-user 404 |
| `adapter/rest/PrometheusEndpointIntegrationTest.java` | Prometheus scrape without token, metric names match alert rules |
| `adapter/rest/WebMapperTest.java` | Session claim parsing, missing/blank/malformed values return null |
| `application/auth/AuthServiceTest.java` | Authentication flow, per-device sessions, token reuse, session revocation |
| `application/auth/SessionCleanupServiceTest.java` | Session cleanup time boundary |
| `application/security/SecurityRejectionStatsServiceTest.java` | Time window truncation and retention boundary |
| `infrastructure/persistence/PersistenceEntityTest.java` | `SessionJpaEntity` maps every column to domain |
| `config/JwtKeyRotationIntegrationTest.java` | Key rotation with `kid`, previous key acceptance |
