# PRD — IAM Service

> Bahasa Indonesia (hanya dokumen ini yang boleh berbahasa Indonesia per standar `team-workflow`).
>
> Dokumen ini mencakup kebutuhan bisnis layanan Identity and Access Management (IAM) yang telah
> diimplementasikan dalam Package A (Fase 1–6). Tujuan PRD ini adalah menjadi Single Source of Truth
> untuk Tahap 1 hingga Tahap 7 alur `team-workflow`.

---

## 1. Ringkasan Eksekutif

IAM Service adalah layanan identitas dan akses berbasis token JWT untuk mengelola pengguna, peran
(RBAC), dan akses ke sistem lain. Layanan ini menyediakan autentikasi, kontrol akses berbasis peran,
jejak audit berkeamanan, sesi per perangkat, rotasi kunci JWT, serta agregat penolakan 401.

Paket A (Fase 1–6) adalah cakupan fitur yang telah disetujui dan diimplementasikan. Paket A **tidak
termasuk** rate limit terdistribusi, pencabutan access token, WORM eksternal, SAML/OIDC,
multi-tenancy, atau pendaftaran mandiri.

---

## 2. Masalah yang Dipecahkan

- Organisasi perlu layaman identitas terpusat untuk mengelola pengguna dan akses ke sistem lain.
- Kebutuhan kontrol akses berbasis peran (RBAC) dengan audit trail yang dapat diverifikasi.
- Kebutuhan manajemen sesi per perangkat agar satu perangkat bisa dicabut tanpa mematikan yang lain.
- Kebutuhan deteksi pemakaian ulang token refresh sebagai indikasi pencurian token.
- Kebutuhan visibilitas lonjakan penolakan 401 (401 aggregate) setelah instance restart.
- Kebutuhan monitoring dan alerting berbasis Prometheus untuk operasional production.

---

## 3. Cakupan Fitur (Package A / Fase 1–6)

### 3.1 Autentikasi & Token

| Fitur | Detail |
|-------|--------|
| JWT HS256 | Access token 15 menit, refresh token 7 hari |
| Refresh token hash | Simpan hash SHA-256, bukan token mentah |
| Rotasi refresh token | Token lama dicabut saat ditukar, token baru terbit |
| Deteksi pemakaian ulang | Token yang dicabut oleh rotasi tapi sesi masih hidup → `REFRESH_TOKEN_REUSE`, cabut seluruh sesi pengguna |
| Rotasi kunci JWT | `kid` pada header, `JWT_SECRET` + `JWT_SECRET_PREVIOUS` selama transisi |
| Token tanpa `kid` | Diterima selama kunci sebelumnya dikonfigurasi |

### 3.2 Pengguna & Peran (RBAC)

| Fitur | Detail |
|-------|--------|
| Manajemen pengguna | Buat, baca, perbarui, hapus, aktifkan, nonaktifkan |
| Manajemen peran | Buat, baca, hapus peran (`ADMIN`, `AUDITOR`, `USER`) |
| Penggantian peran | Admin bisa mengganti seluruh peran milik pengguna |
| Ganti kata sandi | Pemilik atau admin |
| Reset kata sandi | Admin tanpa kata sandi lama |
| Kunci akun otomatis | 5 kali gagal → kunci 15 menit, dihitung per akun |

### 3.3 Sesi Per Perangkat

| Fitur | Detail |
|-------|--------|
| Sesi per login | Setiap login buat satu baris `sessions` dengan id UUID stabil |
| Klaim `session_id` | Token akses dan refresh membawa klaim `session_id` |
| Daftar perangkat | `GET /v1/auth/sessions` hanya sesi milik pemanggil, penanda `current` |
| Pencabutan satu sesi | `DELETE /v1/auth/sessions/{id}`, sesi orang lain 404 |
| Logout | Cabut sesi token yang dikirim + seluruh refresh token di dalamnya |
| Label perangkat | `deviceLabel` opsional, 100 karakter, dari klien (bukan identitas) |

### 3.4 Jejak Audit

| Fitur | Detail |
|-------|--------|
| Rantai hash | Setiap baris audit terikat ke sebelumnya lewat hash (trigger database) |
| Verifikasi rantai | `GET /v1/audit-logs/verify` melaporkan entri rusak pertama |
| Aksi tercatat | Login, logout, refresh, ganti kata sandi, ganti role, aktif/nonaktif, pencabutan massal |
| `TOKENS_REVOKED` | Pencabutan massal dengan aktor dan jumlah token |
| `SESSION_REVOKED` | Pencabutan lewat `DELETE /v1/auth/sessions/{id}` |
| `ACCESS_DENIED` | 403 dengan jejak audit, aktor, dan URI |
| 401 tidak di-audit | Tidak disimpan ke audit_logs (hindari flood oleh pemanggil tidak teridentifikasi) |

### 3.5 Agregat Penolakan 401

| Fitur | Detail |
|-------|--------|
| Tabel agregat | `security_rejection_stats` per menit, method, alasan |
| Alasan | `missing_credentials` atau `invalid_token` |
| Tanpa path/IP | Tidak disimpan, hanya di log WARN |
| Upsert | `ON CONFLICT`, increment counter per menit |
| Pembersihan | Tugas berulang berdasarkan retensi (default 30 hari) |
| Tidak gagal respons | Database error hanya dicatat log, caller tetap terima 401 |

### 3.6 Monitoring & Alerting (Prometheus)

| Fitur | Detail |
|-------|--------|
| Registry Prometheus | `micrometer-registry-prometheus`, expose `/actuator/prometheus` |
| Tanpa token | Scraper tidak perlu refresh access token 15 menit |
| Jaringan internal | Endpoint wajib hanya dijangkau internal scraper |
| Aturan alert | `ops/prometheus/alerts.yml` (down, 5xx, 401 spike, 403 spike, heap, pool) |
| Tes integrasi | `PrometheusEndpointIntegrationTest` mengunci nama metrik |

### 3.7 Operasional & Deployment

| Fitur | Detail |
|-------|--------|
| Docker multi-stage | JRE 21, user non-root 10001:10001, profil prod |
| Backup/restore | Skrip bash dengan `pg_dump`/`pg_restore`, mode container |
| Uji restore CI | `.github/workflows/restore-drill.yml` cadangkan → hapus → pulihkan → verifikasi |
| CODEOWNERS | Pemilik untuk seluruh repo dan path sensitif |
| cspell | Konfigurasi ejaan untuk dokumentasi Bahasa Inggris |

---

## 4. Batasan yang Diketahui (Package A tidak mencakup)

- Rate limit terdistribusi (hanya per instance)
- Pencabutan access token secara langsung (access token berlaku hingga kadaluwarsa)
- WORM / penyimpanan append-only eksternal untuk jejak audit
- Integrasi SAML atau OIDC
- Multi-tenancy
- Pendaftaran mandiri (self-registration)

---

## 5. Kriteria Terima (Acceptance Criteria)

- [ ] Semua endpoint `/v1` teruji dengan integrasi Testcontainers PostgreSQL 16
- [ ] Cakupan kode minimal 80 persen per kelas (JaCoCo)
- [ ] Checkstyle 0 pelanggaran
- [ ] SpotBugs ambang Medium lolos
- [ ] SBOM CycloneDX 1.6 dihasilkan pada `verify`
- [ ] Trivy memindai image Docker (HIGH/CRITICAL dengan perbaikan)
- [ ] Docker image build berhasil dan berjalan sebagai non-root
- [ ] Uji restore otomatis di CI lolos
- [ ] Prometheus scrape mengembalikan metrik yang diharapkan

---

## 6. Rilis

Versi saat ini: `0.0.1-SNAPSHOT`. Rilis pertama `0.1.0` sudah dibuat pada 2026-11-12. Paket A (Fase 1–6) berada di bawah `[Belum Dirilis]` dalam `CHANGELOG.md`.
