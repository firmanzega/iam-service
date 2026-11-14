# WORKLOG

Kartu memori status lintas sesi untuk alur `team-workflow` linear.

## Status Saat Ini (2026-11-14)

### Fase 4 (selesai, commit e4028ff)
- Per-device sessions + 401 rejection aggregate diimplementasikan
- `mvn verify` BUILD SUCCESS, 237 tes hijau, JaCoCo ≥80% per kelas, Checkstyle 0, SpotBugs lolos

### Fase 5 (selesai, commit 33aec68)
- Prometheus registry (`micrometer-registry-prometheus`) ditambahkan ke `pom.xml`
- `/actuator/prometheus` diexpose, tidak butuh token (jaringan internal wajib)
- Aturan alert di `ops/prometheus/alerts.yml`
- Tes integrasi `PrometheusEndpointIntegrationTest` (2 tes)
- `CODEOWNERS` dibuat
- Uji restore otomatis di `.github/workflows/restore-drill.yml`
- `CHANGELOG.md`, `docs/libraries.md`, `docs/security.md`, `docs/deployment.md` diperbarui

### Fase 6 (selesai, commit 9d2a295 + 12f1965)
- ADR 0014: per-device sessions + 401 rejection aggregate
- ADR 0015: Prometheus metrics registry
- `cspell.json` dengan istilah teknis untuk dokumen Bahasa Inggris
- `.mvn/settings.xml` mirror Maven repository ke Maven Central
- `Dockerfile` diperbaiki (copy settings.xml, hapus ca-certificates yang tidak perlu)
- Docker image build berhasil (598MB)
- `node_modules`, `package-lock.json`, `package.json` dihapus dan di-`.gitignore`

### Team-Workflow Adopsi (sedang berjalan)
- Struktur `/docs` baru dibuat sesuai `team-workflow` SKILL.md
- `01-PRD.md` hingga `09-LESSONS-LEARNED.md` + `WORKLOG.md` dibuat
- Folder `docs/` lama tetap ada sebagai referensi (akan disinkronisasi bertahap)

## Tahap Berikutnya
- **Tahap 6**: `senior-qa-engineer` — isi `06-TEST-PLAN.md`, evaluasi release readiness
- **Tahap 7**: `senior-security-engineer` — isi `07-DEPLOYMENT.md`, security audit, hardening

## Pertanyaan Terbuka
- Dokumentasi lama di `docs/` (security.md, deployment.md, manual-testing.md, dll.) akan disinkronisasi ke struktur baru atau diarsipkan?

## Instruksi untuk Sesi Berikutnya
Baca `WORKLOG.md` ini terlebih dahulu, lalu muat `SKILL.md` dari skill yang aktif (Tahap 6: `senior-qa-engineer`).
