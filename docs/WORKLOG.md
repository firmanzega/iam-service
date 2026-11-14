# WORKLOG

Kartu memori status lintas sesi untuk alur `team-workflow` linear.

## Status Saat Ini (2026-11-14)

### Tahap 7 - Security (selesai, commit menunggu)
- docs/07-DEPLOYMENT.md lengkap, CHANGELOG v1.0.0 diperbarui

### Tahap 3 - Arsitektur (selesai)
- ADR-0016 Multi-tenancy (internal tenant_id / external X-Organization-ID), ADR-0017 Structured logging
- Commit `638c1d3` docs: complete Phase 3 design for multi-tenancy and structured logging

### Tahap 4 - Backend (selesai, commit `63814ac`)
- V9__add_tenant_id.sql: tenant_id on users/sessions/refresh_tokens/audit_logs/security_rejection_stats + tenants
- Roles table tetap global; JWT claim tenant_id; TenantHeaderFilter (X-Organization-ID); repository tenant-scoped
- Fix retest QA: `8f125e8` — filter pakai requestURI (getServletPath kosong under MockMvc) + sesi dibuat dengan user.getTenantId()

### Tahap 6 - QA Retest Multi-Tenancy (selesai, commit menunggu)
- 249/249 hijau (1 skipped = BUG-TENANT-001 disengaja), BUILD SUCCESS
- 06-TEST-PLAN.md section 10 ditulis; bugs: TENANT-002 (filter), TENANT-003 (atribusi sesi) — fixed & verified
- Tests baru: TenantResolverTest (5), TenantIsolationIntegrationTest (4 aktif, 1 disabled)
- Commit Tahap 4 (paket A) & fix sudah di git; 06-TEST-PLAN + LL-007 BELUM di-commit

## Keputusan
- Skenario D (self-service) ditunda
- BUG-TENANT-001 & BUG-TENANT-004 TIDAK diperbaiki dulu — butuh keputusan Boss (lihat di bawah)
- Release Package A GO dengan syarat: multi-tenant produksi butuh resolusi BUG-001/004

## Pertanyaan Terbuka (untuk Boss)
1. BUG-TENANT-001: organisasi tidak dikenal sekarang jatuh ke org-default (bukan reject 401/404 seperti ADR-0016 Verification). Kontrak mana yang benar?
2. BUG-TENANT-004: login lookup abaikan tenant (`where username=? or email=?`) — komentar repo mengklaim tenant filter. Identity ambiguity: pengguna "admin" di 2 tenant → siapa yang login? Mau diperbaiki?
3. docs/01-PRD.md belum mencakup multi-tenancy/logging — perlu update Tahap 1?
4. JWT tenant_id claim belum dipakai resolve tenant (context dari header/fallback) — kebutuhan tahap mana?

## Instruksi Sesi Berikutnya
Baca WORKLOG ini dulu. Apapun yang Boss putuskan untuk BUG-TENANT-001/004 + pertanyaan terbuka,
terapkan (Tahap 4), lalu commit docs + fix hasil keputusan. Setelah itu suite dijalankan ulang.
