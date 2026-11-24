# ADR-0024: Structured JSON logging with per-request correlation

- **Status**: Accepted (2026-11-24)
- **Date**: 2026-11-24
- **Decided by**: Boss, `solution-architect` skill
- **Related**: CR-STRUCTLOG-001, `03-ARCHITECTURE.md` §7, ADR-0017 (structured logging categories)

## Context

`LoggingService` already writes through `LoggingAdapter` to SLF4J/Logback, but the
runtime output is a plain console line. `03-ARCHITECTURE.md` §7 claims structured
JSON or key=value output, yet no `logback-spring.xml` exists and `application.yaml`
only customizes `logging.pattern.level`.

OpenTelemetry tracing dependencies are present, but sampling is `0.0` by default
and no collector is deployed. As a result `traceId`/`spanId` are empty in logs,
and there is no generic correlation value for a request. `tenantId` is injected
into MDC only by `LoggingAdapter`, so log lines emitted by framework components
do not carry it.

Verification performed 2026-11-24: the local Spring Boot 4.1.2 JAR contains
`logging.structured.format.console` in `spring-configuration-metadata.json` with
values including `ecs`, `gelf`, and `logstash`. The `StructuredLogEncoder` class
is also present. No new dependency is required.

## Decision criteria

| Criterion | Weight |
|-----------|--------|
| Machine-readable stdout without new dependency | High |
| Correlation available while tracing remains disabled | High |
| MDC cleanup correctness (no cross-request leakage) | High |
| No API contract, schema, or business rule change | High |
| Minimal operational change | Medium |

## Alternatives considered

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| **A. Spring Boot built-in structured logging + MDC correlation** | Verified available in 4.1.2; no new dependency; small diff | Log line format changes; MDC must be cleared carefully | **Adopted** |
| B. Custom `logback-spring.xml` with `StructuredLogEncoder` | Explicit configuration | More XML; same output; no advantage while A works | Fallback only |
| C. Add `logstash-logback-encoder` dependency | Common in older projects | Violates the repo rule of avoiding new dependencies | Rejected |
| D. Enable tracing to obtain `traceId` | Real spans | Requires collector + backend; out of scope | Rejected for this CR |

## Decision

1. Set `logging.structured.format.console: ecs` in `application.yaml` and remove
   the legacy `logging.pattern.level`.
2. Generate a UUID `requestId` at the start of `TenantHeaderFilter` and place it
   in MDC so every request line has a correlation value even when tracing is off.
3. Place the internal `tenant_id` into MDC after successful tenant resolution,
   using the same value as `audit_logs.tenant_id`.
4. Add `logCategory` to MDC in `LoggingAdapter` from `LogEntry.getCategory()`.
5. Clear all three MDC keys in `finally` blocks.
6. Keep OTEL sampling at `0.0`. Tracing backend, Loki, and Service Center are
   explicitly out of scope.

The name `requestId` is used instead of `traceId` to avoid colliding with the
OpenTelemetry MDC key when tracing is enabled later.

## Consequences

**Positive**
- Stdout becomes parseable JSON with `@timestamp`, `log.level`, `message`,
  `service.name`, `requestId`, `tenantId`, and `logCategory`.
- Correlation is available now without waiting for tracing infrastructure.
- Eagerly ready for a future log aggregator without refactoring `LoggingAdapter`.

**Negative / accepted**
- Existing grep-based log inspection must adapt to JSON.
- Log lines are larger than plain text.
- `traceId`/`spanId` remain empty until tracing is enabled.
- `logCategory` covers only entries written through `LoggingService`.

## Trigger for review

Revisit when a log aggregator is introduced, when tracing sampling is enabled, or
when a requirement appears to propagate `tenantId` to framework-originated log
lines beyond the tenant header filter path.

## Status verification

Spring Boot 4.1.2 metadata and `StructuredLogEncoder` were verified from the local
JAR on 2026-11-24. Runtime JSON output and MDC cleanup remain to be verified by
QA in `mvn clean verify` and by manual stdout inspection before checkpoint.
