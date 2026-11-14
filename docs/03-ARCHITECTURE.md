# Architecture Document

> English (technical documentation per `team-workflow` standard).

## 1. Architecture Style

**Clean Architecture / Hexagonal** with strict layer separation:

```
adapter (REST, persistence) -> application (use cases) -> domain (pure business logic)
```

Dependency direction is enforced by `ArchitectureLayerTest`. No framework imports in `domain/`.

## 2. Layer Map

| Layer | Package | Responsibility |
|-------|---------|----------------|
| Domain | `id.osprey.iamservice.domain` | Pure business entities, ports (interfaces), exceptions |
| Application | `id.osprey.iamservice.application` | Use cases, service orchestration |
| Infrastructure | `id.osprey.iamservice.infrastructure` | JPA entities, Spring Data repositories, JDBC adapters |
| Adapter (REST) | `id.osprey.iamservice.adapter.rest` | Controllers, DTOs, exception mapping |
| Config | `id.osprey.iamservice.config` | Security, scheduling, properties, key ring |

## 3. Key Components

### 3.1 Domain

- `domain/auth/Session` — one device login, stable UUID, device label, revoked state
- `domain/auth/RefreshToken` — hashed refresh token, session owner, revoked state
- `domain/auth/SessionRepository` — port for session storage
- `domain/auth/RefreshTokenRepository` — port for refresh token storage
- `domain/auth/TokenIssuer` — port for JWT issuance and hashing
- `domain/security/SecurityRejectionStatRepository` — port for 401 rejection aggregate
- `domain/user/User`, `domain/user/Role`, `domain/user/UserRepository`, `domain/user/RoleRepository`

### 3.2 Application

- `application/auth/AuthService` — login, refresh, logout, list sessions, revoke session, revoke all tokens
- `application/auth/SessionCleanupService` — delete revoked/inactive sessions
- `application/auth/RefreshTokenCleanupService` — delete expired refresh tokens
- `application/auth/RefreshTokenReuseService` — revoke all sessions on token reuse, log `REFRESH_TOKEN_REUSE`
- `application/auth/SessionSummary` — DTO for device list response
- `application/audit/AuditService` — audit logging (transactional and independent)
- `application/security/SecurityRejectionStatsService` — record 401 aggregate, cleanup by retention
- `application/security/PasswordPolicy` — password strength rules

### 3.3 Infrastructure

- `infrastructure/persistence/SessionJpaEntity` — JPA entity for `sessions` table
- `infrastructure/persistence/RefreshTokenJpaEntity` — JPA entity for `refresh_tokens` table
- `infrastructure/persistence/SessionPersistenceAdapter` — implements `SessionRepository`
- `infrastructure/persistence/RefreshTokenPersistenceAdapter` — implements `RefreshTokenRepository`
- `infrastructure/persistence/SecurityRejectionStatPersistenceAdapter` — implements `SecurityRejectionStatRepository` via JdbcTemplate

### 3.4 REST Adapter

- `adapter/rest/AuthController` — login, refresh, logout, sessions list, revoke session
- `adapter/rest/WebMapper` — shared web-to-domain translator (actor ID, session ID, pagination)

### 3.5 Config

- `config/SecurityConfig` — filter chain, permit `/v1/auth/login`, `/v1/auth/refresh`, `/actuator/health`, `/actuator/prometheus`
- `config/RestAuthenticationEntryPoint` — 401 handling, reason classification, WARN log + metric
- `config/RestAccessDeniedHandler` — 403 handling, audit `ACCESS_DENIED`, WARN log + metric
- `config/SecurityEventRecorder` — records security events to log, metric, and audit
- `config/JwtKeyRing` — key rotation with `kid`, current + previous key
- `config/IamProperties` — all `iam.*` configuration properties
- `config/SessionCleanupJob`, `config/RefreshTokenCleanupJob`, `config/SecurityRejectionStatsCleanupJob` — scheduled tasks

## 4. Security Controls

- JWT HS256, access TTL 15 min, refresh TTL 7 days
- Refresh tokens stored as SHA-256 hash
- `kid` = first 8 bytes of SHA-256 of key, hex
- `JWT_SECRET_PREVIOUS` accepted during key rotation
- CSRF disabled (stateless bearer token)
- CORS explicit origins, no wildcard
- HSTS, frame-ancestors 'none', CSP for Swagger UI (dev only)
- Rate limit per IP on auth endpoints, account lock after 5 failures
- 401 not written to audit_logs (avoid flood); 403 written to audit_logs

## 5. Non-Functional Requirements

| NFR | Value |
|-----|-------|
| Test coverage | ≥ 80% line per class (JaCoCo) |
| Checkstyle | 0 violations |
| SpotBugs | Medium threshold, per-class exclusions |
| SBOM | CycloneDX 1.6 JSON on `verify` |
| Docker | Multi-stage, JRE 21, user 10001:10001, prod profile |
| Scheduling pool | 4 threads (4 cleanup tasks) |

## 6. Known Limits

- Rate limit is per instance (no distributed rate limiting in Package A)
- Access tokens are not revocable directly (expire after 15 minutes max)
- No WORM / append-only external audit storage
- No SAML/OIDC, no multi-tenancy, no self-registration
