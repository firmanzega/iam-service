# WORKLOG

Kartu memori status lintas sesi untuk alur `team-workflow` linear.

## Status Saat Ini (2026-11-14)

### Fase 4-6 (selesai, di-commit)
- Fase 4 (e4028ff): per-device sessions + 401 rejection aggregate
- Fase 5 (33aec68): Prometheus registry, alerts, restore drill CI, CODEOWNERS
- Fase 6 (9d2a295, 12f1965): ADR 0014/0015, cspell, Docker build fix

### Team-Workflow Adopsi (selesai)
- Struktur `/docs` baru dibuat sesuai team-workflow SKILL.md
- 01-PRD.md hingga 09-LESSONS-LEARNED.md + WORKLOG.md dibuat
- cspell.json diperbarui (WORKLOG.md dikecualikan, Bahasa Indonesia)
- Folder docs/ lama tetap sebagai referensi

### Tahap 6 - QA (selesai, commit f820db6)
- docs/06-TEST-PLAN.md lengkap: analisis risiko, 13 test case, hasil eksekusi, release readiness GO
- mvn clean verify BUILD SUCCESS, 237+ tes hijau, JaCoCo ≥80%, Checkstyle 0, SpotBugs lolos

### Tahap 7 - Security (selesai, commit menunggu)
- docs/07-DEPLOYMENT.md lengkap: secret management, Docker, Prometheus exposure, security headers, checklist, migration, backup/restore, security audit results, incident runbook
- CHANGELOG.md diperbarui dengan rilis v1.0.0
- cspell 0 issues pada semua dokumen Bahasa Inggris baru

## Tahap Berikutnya
- Tidak ada tahap berikutnya dalam team-workflow (Tahap 7 adalah final)

## Keputusan: Skenario D (Self-Service Pengguna Akhir) Ditunda

| Pertanyaan | Jawaban Boss | Keputusan |
|------------|--------------|-----------|
| Lupa kata sandi via email dibutuhkan segera? | Boleh ditunda dulu | Ditunda |
| MFA/2FA dibutuhkan untuk self-service? | Kedepannya perlu, tidak untuk saat ini | Ditunda |
| Self-registration diizinkan? | Tidak, dibuat admin dulu | Tidak diizinkan Paket A |

**Kesimpulan:** Skenario D ditunda. Endpoint self-service dasar yang sudah ada (ganti kata sandi, daftar perangkat, cabut perangkat) cukup untuk sekarang. Portal self-service terpisah dapat dibangun nanti tanpa mengubah backend.

## Tahap 3 - Arsitektur (selesai, checkpoint)
- ADR-0016: Multi-tenancy — internal `tenant_id`, external `X-Organization-ID` header
- ADR-0017: Structured logging — 5 categories (errors, generals, integrations, timers, emails)
- docs/03-ARCHITECTURE.md: added TenantContext, TenantResolver, LoggingService, tenant_id on core tables, C4 diagrams, logging categories
- docs/04-API-SPEC.yaml: added `X-Organization-ID` header to /auth/logout, /auth/sessions, /auth/sessions/{sessionId}

## Pertanyaan Terbuka
- docs/01-PRD.md perlu diperbarui untuk mencakup multi-tenancy dan logging sebagai kebutuhan Package B (Tahap 1 harus selesai sebelum implementasi Tahap 4)
- Identifikasi tenant disetujui: header `X-Organization-ID` (external), `tenant_id` (internal)
- Skala: < 5 tenant, self-managed logging (stdout/file), email ditunda

## Instruksi Sesi Berikutnya
Baca WORKLOG.md ini terlebih dahulu. Jika Boss setuju, lanjut ke Tahap 4 (implementasi backend): tenant_id migration, TenantResolver, LoggingService, repository tenant-scoped queries.
