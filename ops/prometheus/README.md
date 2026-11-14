# Prometheus Integration

The service exposes metrics in the Prometheus text format at
`/actuator/prometheus`. Prometheus scrapes the service; the service does not
push metrics anywhere.

## Scrape configuration

```yaml
scrape_configs:
  - job_name: iam-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["iam-service:9090"]
```

`9090` is the port used by the `prod` profile. Adjust the target to the address
the scraper can reach.

The endpoint does not require a token, because a scraper cannot refresh a
15-minute access token. That is only acceptable while the endpoint stays on an
internal network. Do not publish `/actuator/prometheus` through the public
ingress, and do not rely on the application to hide it. See the exposure table
in `docs/deployment.md`.

## Metric contract used by the alerts

| Metric | Labels used by alerts | Meaning |
|---|---|---|
| `iam_security_rejected_total` | `reason`, `method` | Security rejections. `reason` is `unauthorized` for 401 and `access_denied` for 403 |
| `http_server_requests_seconds_count` | `status`, `method`, `uri` | HTTP response counts, used for the 5xx ratio |
| `hikaricp_connections_pending` | none | Threads waiting for a database connection |
| `jvm_memory_used_bytes`, `jvm_memory_max_bytes` | `area` | Heap usage |

`iam.security.rejected` is the Micrometer name; the Prometheus registry adds the
`_total` suffix for counters. `PrometheusEndpointIntegrationTest` fails if these
names ever stop being exported, so the alert rules cannot drift silently.

## Alert rules

`alerts.yml` contains the rules. Validate it before deploying:

```bash
promtool check rules ops/prometheus/alerts.yml
```

Rules that are intentionally absent:

- **Latency alerts.** A latency threshold needs a histogram, and
  `http.server.requests` is not configured with
  `management.metrics.distribution.percentiles-histogram`. Adding that setting
  increases the number of series exported, so it is a deliberate decision, not
  an oversight.
- **Audit chain tampering.** The chain is verified by an endpoint, not by a
  continuously running metric. Schedule the verification described in
  `docs/runbook.md` instead of alerting on a metric that does not exist.
