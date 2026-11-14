# Front-End UI Document

> English (technical documentation per `team-workflow` standard).

## Status

**Not applicable.** IAM Service is a **backend-only REST API**. There is no front-end
application in this repository. Phase 2 (UI/UX Design) and Phase 5 (Front-End Dev)
are skipped for this project.

All clients consume the REST API directly. API documentation is available via
OpenAPI/Swagger UI in the `dev` profile only.

## Client Integration Notes

- Tokens are sent via `Authorization: Bearer <access_token>` header.
- Refresh token is sent in the request body of `POST /v1/auth/refresh` and `POST /v1/auth/logout`.
- `deviceLabel` is optional on login (max 100 characters).
- `GET /v1/auth/sessions` returns active sessions with a `current` flag on the session matching the bearer token.
- `DELETE /v1/auth/sessions/{id}` returns 204 for owned sessions, 404 for other users' sessions or non-existent sessions.
