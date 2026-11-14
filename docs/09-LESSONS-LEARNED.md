# Lessons Learned

> English (technical documentation per `team-workflow` standard).

Lessons learned during Package A (Phase 1–6) implementation. Status: Pending Review.

| ID | Date | Phase | Lesson | Status |
|----|------|-------|--------|--------|
| LL-001 | 2026-11-13 | Phase 4 | `AuthService.revokeSession(UUID, Instant)` must not call `revokeAllForSession` twice when session row still exists. Use `ifPresentOrElse` so tokens are still revoked when session row was already deleted by cleanup. | Pending Review |
| LL-002 | 2026-11-13 | Phase 4 | SpotBugs `EI_EXPOSE_REP2` exclusions must name class + pattern precisely, not disable globally. New classes are still checked. | Pending Review |
| LL-003 | 2026-11-14 | Phase 5 | `maven.java.net` is unreachable from this network (connection closed), not just certificate-expired. Fix: mirror all external repositories to Maven Central via `.mvn/settings.xml` in container. | Pending Review |
| LL-004 | 2026-11-14 | Phase 5 | `/actuator/prometheus` must not require token because scraper cannot refresh 15-minute access token. Network isolation is mandatory. | Pending Review |
| LL-005 | 2026-11-14 | Phase 6 | `team-workflow` skill exists as file but cannot be loaded via `tools.skill`. Used as reference documentation instead. | Pending Review |
| LL-006 | 2026-11-14 | Tahap 4 | Raw JDBC test inserts into `audit_logs`/`refresh_tokens` must include `tenant_id` (V9 NOT NULL). Tenant-scoped reads with empty `TenantContext` silently return an empty page, so integration tests must send `X-Organization-ID: org-default` (or set context). Scheduler cleanups run without context and therefore use the global single-arg variants. | Pending Review |
