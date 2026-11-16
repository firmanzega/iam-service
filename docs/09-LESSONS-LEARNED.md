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
| LL-006 | 2026-11-14 | Phase 4 | Raw JDBC test inserts into `audit_logs`/`refresh_tokens` must include `tenant_id` (V9 NOT NULL). Tenant-scoped reads with empty `TenantContext` silently return an empty page, so integration tests must send `X-Organization-ID: org-default` (or set context). Scheduler cleanups run without context and therefore use the global single-arg variants. | Pending Review |
| LL-007 | 2026-11-14 | Phase 6/4 | `request.getServletPath()` is empty under MockMvc — filters that match on it silently never fire. Match on `request.getRequestURI()` (strip context path, fall back to pathInfo) instead. Also, never attribute tenant to the request fallback at login: a public endpoint cannot rely on a header that doesn't exist there; use the owning user's `tenant_id`. | Pending Review |
| LL-008 | 2026-11-16 | Stage 7/v1.1.0 | PowerShell `>` redirection transcodes: `git show HEAD:file > out.md` writes UTF-16LE, silently corrupting a UTF-8 file. Use `cmd /c "git show ... > out.md"` (raw bytes) or `git cat-file` piped through tooling that does not transcode; verify BOM/line endings with `Format-Hex` before editing. | Pending Review |
| LL-009 | 2026-11-16 | Stage 7/v1.1.0 | A JWT claim that is written but never read is not a security control — bind it (e.g. token claim == tenant from header) or treat the header as the only tenant source and document it as partition-only. "Isolation valid at repository layer" says nothing about caller authorization. | Pending Review |
| LL-010 | 2026-11-16 | Stage 7/v1.1.0 | Docker Desktop for Windows creates an enabled inbound firewall rule `Docker Desktop Backend` (TCP+UDP, any port, Any remote address, Public profile); a port published in compose is therefore reachable LAN-wide by default. Verify published ports from the host (`Get-NetTCPConnection`, `Get-NetFirewallRule`) and consider unpublishing or network-unifying instead of relying on host firewall policy. | Pending Review |
| LL-011 | 2026-11-16 | Stage 7/v1.1.0 | Spring Boot canonical env-var binding removes dashes: `server.ssl.key-store-password` maps to `SERVER_SSL_KEYSTOREPASSWORD`, NOT `SERVER_SSL_KEY_STORE_PASSWORD`. Wrong names boot "successfully" with SSL enabled but empty credentials -> `UnrecoverableKeyException` only at Tomcat connector start. Generated Java keystores/credentials via env must be verified by an actual TLS handshake, not by reading config. | Pending Review |
| LL-012 | 2026-11-16 | Stage 7/v1.1.0 | PowerShell strips embedded double quotes when passing arguments to native commands (curl `-d '{"a":1}'` arrives as `-d {a:1}`). Use a temp file with `curl -d @file` instead of inline JSON. | Pending Review |
