# ADR 0015 — Prometheus metrics registry and scrape-based monitoring

- **Status**: Adopted
- **Date**: 2026-11-14
- **Decided by**: Boss (product/engineering), implemented in Package A Phase 5

## Context

The service exposes operational metrics through `/actuator/metrics`, which is
read on demand and authenticated as ADMIN. Prometheus cannot consume that
endpoint for alerting because it needs a time series, a scrape interval, and a
counter suffix. The service also did not push metrics anywhere.

Package A needed alerting on sustained 401 spikes, repeated 403 attempts, 5xx
ratios, heap pressure, and database connection pool waits. Those alerts require
a metrics registry that Prometheus can scrape.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| **A. micrometer-registry-otlp (push to collector)** | Unified with tracing | Metrics leave the service without an operator asking; needs a collector running |
| **B. micrometer-registry-prometheus (chosen)** | Prometheus pulls metrics, standard scrape model, no outbound metric traffic | Adds one runtime dependency; endpoint must be protected by network, not token |
| **C. Actuator metrics only** | No new dependency | No time series, no alerting, counters reset on restart |
| **D. Custom exporter sidecar** | Full control over what is exported | Extra container, extra maintenance, not needed |

## Decision

**We adopt B.**

`micrometer-registry-prometheus` is added with `runtime` scope because no
production code references its classes directly; Actuator auto-configuration
uses it. `/actuator/prometheus` is exposed alongside `health` and `metrics`.

The endpoint requires no token because a scraper cannot refresh a 15-minute
access token. That is acceptable only while the service is unreachable from the
public network, so `/actuator/prometheus` must not be published through the
public ingress. This is documented in docs/deployment.md and docs/security.md.

Micrometer name `iam.security.rejected` becomes `iam_security_rejected_total` in
Prometheus. Alert rules live in `ops/prometheus/alerts.yml` and use selectors on
`job="iam-service"`. `PrometheusEndpointIntegrationTest` fails if these metric
names ever stop being exported, so alert rules cannot drift silently.

Latency alerts are intentionally absent: `http.server.requests` is not configured
with distribution percentiles, and adding that increases exported series.
Audit chain tampering is not alerted by metric; it is verified by the
`/v1/audit-logs/verify` endpoint on a schedule.

## Impact

**Positive**
- Alerts on security spikes, 5xx ratio, heap pressure, and pool waits
- Metrics survive restart because Prometheus stores the time series
- No outbound metric traffic; traffic direction is pull, not push

**Negative**
- One extra runtime dependency
- `/actuator/prometheus` has no authentication; network isolation is mandatory
- Scraper network must reach port 9090 (prod profile)

**Rejected alternatives**
- A mixes metrics with tracing and pushes without a scrape request
- C cannot alert
- D is over-engineered for this service

## Verification

- `PrometheusEndpointIntegrationTest` (2 tests): scrape returns 200 without a
  token; `/actuator/metrics` still requires authentication; body contains
  `iam_security_rejected_total`, `http_server_requests_seconds_count`,
  `jvm_memory_used_bytes`, `hikaricp_connections_pending`
- `mvn verify` Package A: BUILD SUCCESS, Checkstyle 0, SpotBugs passed,
  JaCoCo >= 80% per class
- `ops/prometheus/README.md` documents scrape config and the metric contract

## Related notes

- ADR 0013 covers observability of rejections and tracing
- docs/deployment.md section 4.3 documents the Prometheus exposure checklist
- docs/security.md lists the unauthenticated prometheus endpoint as a known limit
