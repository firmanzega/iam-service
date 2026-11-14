# WORKLOG

Kartu memori status lintas sesi untuk alur `team-workflow` linear.

## Status Saat Ini (2026-11-14)

### Tahap 7 - Security (selesai, commit menunggu)
- docs/07-DEPLOYMENT.md lengkap, CHANGELOG v1.0.0 diperbarui

### Tahap 3 - Arsitektur (selesai, checkpoint)
- ADR-0016: Multi-tenancy — internal `tenant_id`, external `X-Organization-ID` header
- ADR-0017: Structured logging — 5 categories (errors, generals, integrations, timers, emails)
- docs/03-ARCHITECTURE.md, docs/04-API-SPEC.yaml disesuaikan

### Tahap 4 - Backend (selesai, menunggu commit Boss)
- V9__add_tenant_id.sql: tenant_id on users/sessions/refresh_tokens/audit_logs/security_rejection_stats + tenants table
- Roles table tetap global (tidak pakai tenant_id, lihat ADR-0016 §6.2)
- Domain: Tenant, TenantContext, TenantResolver, logging ports; application: tenant-aware services
- JWT claim `tenant_id` ditambahkan di TokenClaims/JwtTokenService
- TenantHeaderFilter: endpoint tenant-required menolak 400 tanpa header
- Repository queries difilter tenant_id; scheduler pakai variant global
- Perbaikan sesi ini: SecurityRejectionStatPersistenceAdapter (find+update, ganti ON CONFLICT), TenantJpaEntity.createdAt, fallback currentTenantId(), test raw SQL insert & header X-Organization-ID
- ./mvnw test BUILD SUCCESS, 239/239 hijau

## Keputusan
- Skenario D (self-service) ditunda; paket self-service terpisah nanti
- Multi-tenancy: `X-Organization-ID` external, `tenant_id` internal; <5 tenant; stdout/file logging

## Pertanyaan Terbuka
- docs/01-PRD.md belum mencakup multi-tenancy/logging (Tahap 1 harus update dulu bila masuk scope)
- JWT tenant_id claim belum dipakai untuk resolve tenant; semua konteks lewat header/fallback org-default. Perlu diputuskan apakah jwt-resolve termasuk Tahap 4 atau tahap berikut
- cspell source code baseline 8009 isu (komentar Bahasa Indonesia pra-existing) — bukan dari Tahap 4
- Semua perubahan Tahap 4 BELUM di-commit

## Instruksi Sesi Berikutnya
Baca WORKLOG ini dulu. Setelah Boss review, commit tahap 4 dengan Conventional Commits
(mis. `feat(tenancy): add tenant_id migration and tenant-aware repositories`),
lalu putuskan apakah perlu update docs/01-PRD.md (Tahap 1) sebelum lanjut ke tahap berikut.
