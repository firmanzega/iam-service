# ADR-0025: Audit write-freeze maintenance mode

- **Status**: Accepted (2026-11-25)
- **Date**: 2026-11-25
- **Decided by**: Boss, `solution-architect` skill
- **Related**: CR-MAINT-001, ADR-0024 (structured logging), `03-ARCHITECTURE.md` §7 and §11

## Context

A future change (CR-2) will add `requestId` to `audit_logs` and recalculate the
hash chain. The backfill must run in `id` order; any audit row written with the
old hash function during the backfill would break the chain. Today the only way
to stop audit writes is to stop the whole application, which also stops
`/actuator/health` and can make an orchestrator restart the container mid-backfill.

Boss approved a runtime maintenance flag that freezes API traffic and suppresses
audit writes, built now before `audit_logs` grows.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| No audit row can be written during freeze | High |
| Healthcheck stays available during freeze | High |
| Default-deny for future API versions and new endpoints | High |
| Toggle callable only by OPS and traceable | High |
| No new dependency, no schema change, no public API contract change | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Deny all requests, explicit allowlist, three audit-write layers** | Future-proof; simple rule; health survives | Mutations unavailable during freeze; audit incomplete for the window | **Adopted** |
| B. Stop the whole application | Zero code | Health dies; orchestrator may restart; no runtime toggle | Rejected |
| C. Deny only `/v1/**` by prefix | Small diff | Forgets `/v2/**` and future paths; 403 on internal paths still writes audit | Rejected |
| D. Pause only write methods | Reads keep working | GET paths that return 403 still call `recordIndependently` | Rejected |

## Decision

1. `MaintenanceState` is the single source of truth, initialized from
   `iam.maintenance.audit-freeze` (default `false`) and toggled at runtime by OPS.
2. `AuditWriteFreezeFilter` runs after `TenantHeaderFilter` and returns **503** for
   **every** request unless it is in the allowlist:
   - `GET /actuator/health` (healthcheck)
   - `/internal/maintenance/**` (the toggle, OPS only — the only way to unfreeze)
3. The rule is **default deny**, not a `startsWith("/v1/")` list. Future `/v2/**`
   and new endpoints are frozen automatically.
4. Second layer: `AuditService.record()` and `recordIndependently()` throw
   `AuditSuppressedException` when frozen, increment `iam.audit.suppressed`.
5. Third layer: `SecurityEventRecorder.recordAccessDenied()` skips persisting
   `ACCESS_DENIED` for `/internal/maintenance/**`, increment
   `iam.maintenance.denied`. The 403 response itself is unchanged.
6. Toggle endpoints write to application log and Prometheus metrics, **never** to
   `audit_logs`. The 503 uses the existing `ErrorResponse` plus `Retry-After: 120`.
7. `04-API-SPEC.yaml` is not changed; `/internal/**` is documented in the runbook.

## Consequences

**Positive**
- Backfill hash can run without stopping the container or its healthcheck.
- Future API versions need no filter change.
- Every suppression is visible in logs and metrics.

**Negative / accepted**
- All API traffic returns 503 during the freeze, including reads and auth.
- Some security events (`ACCESS_DENIED` on internal paths) are not recorded in
  `audit_logs`; this must be kept short and documented in the runbook.
- A forgotten freeze leaves the service unusable; `GET /internal/maintenance`
  and the `iam.maintenance.toggled` metric are the detection controls.

## Failure modes

| Condition | Behavior |
|-----------|----------|
| Filter throws | Request fails closed; no audit write |
| `AuditSuppressedException` reaches `SecurityEventRecorder` | Caught by its existing `RuntimeException` handler; caller still sees 403 |
| App restarts while frozen | State reinitializes from env (default `false`) — safe fail-open to normal |
| Toggle called by non-OPS | 403, no audit row, `iam.maintenance.denied` increments |

## Trigger for review

Revisit when a log aggregator introduces a different maintenance control, when
a future endpoint must remain reachable during freeze (add to allowlist
explicitly), or when CR-2 changes the hash payload.

## Status verification

No runtime verification yet; QA must run the 18 acceptance cases and the
mutation proof before checkpoint.
