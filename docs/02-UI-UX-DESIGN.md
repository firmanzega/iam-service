# UI/UX Design Document

> English (technical documentation per `team-workflow` standard).

This project is a **backend-only IAM service**. There is no user interface.
Phase 2 (UI/UX) and Phase 5 (Front-End Dev) are **not applicable** for this
service. All clients consume the REST API directly.

## 1. Scope

- No frontend application exists in this repository.
- API consumers are expected to be external clients (mobile, web, system-to-system).
- API documentation is provided via OpenAPI/Swagger UI in development profile only.

## 2. API Authentication Flow (for clients)

```
Client → POST /v1/auth/login (identifier, password, deviceLabel?)
         → 200 + { accessToken, refreshToken }

Client → GET /v1/auth/sessions (Authorization: Bearer accessToken)
         → 200 + [ { id, deviceLabel, current, ... } ]

Client → DELETE /v1/auth/sessions/{id} (Authorization: Bearer accessToken)
         → 204 (own session) or 404 (other user's session)

Client → POST /v1/auth/refresh (refreshToken)
         → 200 + { accessToken, refreshToken } (old refresh token revoked)

Client → POST /v1/auth/logout (Authorization: Bearer accessToken, refreshToken)
         → 204 (whole session revoked)
```

## 3. Design Decisions

- **Stateless backend**: No server-side sessions for UI rendering. JWT is stateless.
- **No cookies for tokens**: Tokens are sent via `Authorization: Bearer` header.
- **Swagger UI disabled in production**: `springdoc` is disabled in `prod` profile.
- **CORS**: Explicit origin list, no wildcard.
