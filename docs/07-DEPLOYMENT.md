# Deployment Guide

> English (technical documentation per `team-workflow` standard).
>
> Output of **Phase 7** (`senior-security-engineer`). This file is a skeleton to be filled by Security.

## 1. Secret Management

- `JWT_SECRET` must not be on disk. Supplied via environment variable from secret manager.
- `JWT_SECRET_PREVIOUS` only during key rotation transition.
- `POSTGRES_PASSWORD` from secret manager.

## 2. Docker Image

- Multi-stage: JDK 21 build, JRE 21 runtime
- Runs as user 10001:10001 (non-root)
- `SPRING_PROFILES_ACTIVE=prod`
- Exposes port 9090

## 3. Prometheus Exposure

- `/actuator/prometheus` is exposed without token
- **MUST** only be reachable from internal scraper network
- Must NOT be published through public ingress

## 4. Security Checklist

To be completed by `senior-security-engineer` in Phase 7.
