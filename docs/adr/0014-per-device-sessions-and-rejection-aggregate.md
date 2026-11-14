# ADR 0014 — Per-device sessions and 401 rejection aggregate

- **Status**: Adopted
- **Date**: 2026-11-14
- **Decided by**: Boss (product/engineering), implemented in Package A Phase 4

## Context

The service issues one refresh token per user, shared across all devices. There was
no stable identity for "this login on this phone", so revoking one device meant
guessing which refresh token belonged to it, or revoking all devices at once.

At the same time, 401 rejections (missing or invalid credentials) were only logged
to WARN and counted in a Micrometer counter that lives in memory. After a restart,
an operator could not answer "when did the spike happen" without an external log
system. Storing every rejected request in the audit table was rejected because
unauthenticated requests can be sent by anyone, so the audit table could be
flooded by external traffic and bury the real events.

Package A was approved with per-device sessions and a per-minute 401 rejection
aggregate as part of the `/v1` versioned API contract.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| **A. One refresh token per user, revoke all on logout** | Simple, no new table | Cannot revoke a single device; lost token revokes every session |
| **B. Device sessions table + session_id claim (chosen)** | One row per login, stable session id survives token rotation, single-device revocation | New table and migration; access token issued before migration has no session_id |
| **C. Store every 401 in audit_logs** | Complete per-request trail | Table can be flooded by anyone; no identity to attach to unauthenticated requests |
| **D. Per-minute aggregate only, no path/IP (chosen)** | Bounded growth, cannot be inflated by callers, survives restart via database | No path or IP; detail remains in WARN logs only |
| **E. Prometheus counter only** | Already exists for in-memory dashboards | Lost on restart, no historical window |

## Decision

**We adopt B and D.**

A new `sessions` table holds one row per device login:
- `id` UUID primary key, stable across refresh-token rotations
- `user_id` references `users(id)`
- `device_label` VARCHAR(100), caller-supplied hint only, not an identity
- `created_at`, `last_seen_at`, `revoked_at`

Access and refresh tokens carry a `session_id` claim pointing at the row. Tokens
issued before this change have no `session_id` and are treated as session-less,
not invalid.

`GET /v1/auth/sessions` lists active sessions owned by the caller, with a
`current` flag on the session whose token was sent. `DELETE /v1/auth/sessions/{id}`
revokes one owned session; sessions owned by another user return 404, identical
to a non-existent session, to prevent id guessing.

Logout revokes the whole session of the sent token plus every refresh token in
it. Other devices of the same user are unaffected. Token reuse caused by rotation
(while the session is still alive) is still `REFRESH_TOKEN_REUSE`; logout or
session revocation does not trigger it.

A new `security_rejection_stats` table holds one row per minute, HTTP method, and
reason (`missing_credentials` or `invalid_token`). No path, no client IP. The
row is upserted with `ON CONFLICT`, so repeated rejections in the same minute
increment a counter instead of adding rows. A scheduled job removes rows older
than `iam.security-rejection-stats.retention` (default 30d).

Writing the aggregate never fails the response: database errors are logged and
the caller still receives 401.

## Impact

**Positive**
- A lost device can be revoked without touching other devices
- 401 spikes are visible after restart via `security_rejection_stats`
- Callers cannot inflate the aggregate table with arbitrary paths or IPs
- Session list gives the account owner visibility before revoking anything

**Negative**
- New `sessions` and `security_rejection_stats` tables require migration `V8`
- Access tokens issued before migration lack `session_id` until next refresh
- Revocation does not invalidate already-issued access tokens (they live up to
  15 minutes); a central revocation list does not exist yet
- 401 attempts are not traceable to an actor in audit_logs

**Rejected alternatives**
- A is too coarse for "one device is lost"
- C floods the audit table with unauthenticated traffic
- E loses history on restart

## Verification

- `SessionManagementIntegrationTest` (6 tests): two logins, `current` flag,
  single-device logout, other-user session returns 404
- `AuthSecurityIntegrationTest` (30 tests): authorization matrix including
  session endpoints and 401 aggregate
- `WebMapperTest` (9 tests): `session_id` claim parsing, missing/blank/malformed
  values return null
- `PersistenceEntityTest`: `SessionJpaEntity` maps every column to domain
- `mvn verify` Package A: BUILD SUCCESS, 237 tests green, JaCoCo >= 80% per
  class, Checkstyle 0, SpotBugs passed

## Related notes

- ADR 0013 covers observability of rejections and tracing
- docs/security.md section 1 documents the security model and limits
- docs/runbook.md section 9.3 covers the rejection aggregate table
- ops/prometheus/alerts.yml alerts on `iam_security_rejected_total`
