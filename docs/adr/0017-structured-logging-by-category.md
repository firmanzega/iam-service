# ADR 0017 — Structured logging by category (errors, generals, integrations, timers, emails)

- **Status**: Adopted
- **Date**: 2026-11-14
- **Decided by**: Boss (product/engineering), solution-architect skill

## Context

`iam-service` Package A logs to stdout/file via Spring Boot defaults (Logback) and
exposes metrics via Prometheus. There is no structured, categorized operational
log. When something goes wrong, operators must grep through mixed application logs
to find errors, integration request/response pairs, background job executions, or
manual developer messages.

The service needs categorized logging so operators and developers can trace:
1. Errors — unhandled exceptions, system errors, custom error messages
2. Generals — general log messages or custom messages sent manually by developers via `LogMessage` action
3. Integrations — external REST API communication, request and response, both inbound and outbound
4. Timers — background job / scheduled task execution history: success, failure, running, timeout
5. Emails — email send status via SMTP: success, failure, trouble (deferred until email feature is implemented)

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| No new runtime dependencies (use existing SLF4J/Logback) | High |
| Structured output parseable by log aggregators (JSON or key=value) | High |
| Category separation for filtering and alerting | High |
| Self-managed storage (stdout/file), no external aggregator required initially | High |
| Email category scaffold ready but not implemented | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. In-app structured logging via SLF4J/Logback + `LoggingService` domain (chosen)** | No new dependency, uses existing stack, self-managed, categories via logger names or MDC | Logs live on instance only; needs external aggregator for multi-instance central view | **Adopted** — fits small scale, self-managed requirement |
| B. External aggregator (ELK, Loki, CloudWatch) | Centralized search, dashboard, alerting | Extra infrastructure, operational cost, vendor lock-in | Rejected — not needed for < 5 tenants; can be added later |
| C. Database table for logs | Queryable via SQL, ACID | High write volume, table bloat, couples logging to DB availability | Rejected — logging should not depend on the same DB it protects |

## Decision

**We adopt Alternative A.**

- Existing SLF4J + Logback (Spring Boot default) is used; no new dependency
- A `LoggingService` domain port is added with methods per category:
  - `error(message, exception?, context?)` — errors and unhandled exceptions
  - `general(message, context?)` — general/developer custom messages via `LogMessage` action
  - `integration(request, response, endpoint, duration?)` — external REST API communication
  - `timer(jobName, status, duration?, error?)` — background job execution history (success/failed/running/timeout)
  - `email(recipient, status, error?)` — email send status (scaffold only; SMTP implementation deferred)
- Log output: structured JSON or `key=value` to stdout; file rotation via Logback
- `organization_id` / `tenant_id` is injected into every log record via MDC for multi-tenant traceability
- `traceId` / `spanId` from OpenTelemetry tracing is included when sampling is enabled
- Email category is defined in the interface but implementation is deferred until the email/SMTP feature is ordered

## Consequences

**Positive**
- Zero new dependencies; uses already-declared SLF4J/Logback
- Self-managed: stdout/file, no external service required
- Structured output can be shipped to an aggregator later without code changes
- Category separation enables targeted alerting (e.g., alert on `errors` category spike)
- Tenant ID and trace ID in every log record supports multi-tenant observability

**Negative**
- Logs are local to each instance; no central view without aggregator
- Log rotation and retention must be managed by the operator (Logback config or external logrotate)
- No log search UI out of the box

**Rejected alternatives**
- B rejected due to operational cost and no current requirement for central log dashboard
- C rejected because logging must remain available even when the database is under pressure

## Verification

- Unit tests for `LoggingService` implementations assert each category writes with correct level and context
- Integration tests assert `X-Organization-ID` / `tenant_id` appears in log MDC when set
- `timer` category test asserts job status values: `STARTED`, `SUCCESS`, `FAILED`, `TIMEOUT`
- `email` category exists in interface but no SMTP test (deferred)

## Trigger for review

- More than 3 service instances → add external log aggregator (Alternative B)
- Email feature ordered → implement SMTP logging in `email` category
- Need for log search dashboard → evaluate ELK/Loki/CloudWatch

## Related notes

- ADR 0013 covers observability of rejections and tracing (OpenTelemetry bridge)
- docs/03-ARCHITECTURE.md will be updated with `LoggingService` component
- docs/04-API-SPEC.yaml may be updated if logging endpoints are exposed via API