# Test Plan

> English (technical documentation per `team-workflow` standard).
>
> Output of **Phase 6** (`senior-qa-engineer`). This file is a skeleton to be filled by QA.

## 1. Scope

Backend-only IAM service. No front-end to test. All testing is API-level.

## 2. Test Levels

| Level | Tool | Status |
|-------|------|--------|
| Unit test | JUnit 5 + Mockito | Green (existing tests) |
| Integration test | MockMvc + Testcontainers PostgreSQL 16 | Green (existing tests) |
| Prometheus scrape test | MockMvc + real servlet container | Green (PrometheusEndpointIntegrationTest) |

## 3. Coverage Gate

- JaCoCo line coverage ≥ 80% per class (enforced in `mvn verify`)
- Excluded: `IamServiceApplication` (entry point only)

## 4. Static Analysis Gate

- Checkstyle: 0 violations
- SpotBugs: Medium threshold, per-class exclusions only

## 5. Release Readiness

To be completed by `senior-qa-engineer` in Phase 6.
