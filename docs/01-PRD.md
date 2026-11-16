# PRD — IAM Service

> Bahasa Indonesia (hanya dokumen ini yang boleh berbahasa Indonesia per standar `team-workflow`).
>
> Dokumen ini mencakup kebutuhan bisnis layanan Identity and Access Management (IAM) yang telah
> diimplementasikan dalam Package A (Fase 1–6) serta Paket Hardening Keamanan v1.1.0 (2026-11-16).
> Tujuan PRD ini adalah menjadi Single Source of Truth untuk Tahap 1 hingga Tahap 7 alur `team-workflow`.

---

## 1. Ringkasan Eksekutif

IAM Service adalah layanan identitas dan akses berbasis token JWT untuk mengelola pengguna, peran
(RBAC), dan akses ke sistem lain. Layanan ini menyediakan autentikasi, kontrol akses berbasis peran,
jejak audit berkeamanan, sesi per perangkat, rotasi kunci JWT, serta agregat penolakan 401.

Paket A (Fase 1–6) adalah cakupan fitur yang telah disetujui dan diimplementasikan, termasuk
multi-tenancy (isolasi logis per organisasi, header `X-Organization-ID`) dan structured logging
(logging terstruktur dengan kategori) yang diadopsi pada 2026-11-14 melalui ADR-0016 dan ADR-0017.

Versi `1.1.0` (2026-11-16) menambahkan **paket hardening keamanan** di atas fondasi Package A:
binding klaim `tenant_id` pada access token terhadap tenant hasil resolusi header (ADR-0019),
penegakan `token_type=access` pada decoder (refresh token tidak lagi bisa dipakai sebagai Bearer),
pembatasan `GET /v1/audit-logs/verify` ke peran `OPS` (V12), serta HTTPS in-app di production
(TLS dengan CA internal, service `iam.osprey.service`). Lihat §3.10 dan §6.

Paket A **tidak termasuk** rate limit terdistribusi, pencabutan access token, WORM eksternal,
SAML/OIDC, atau pendaftaran mandiri.

---

## 2. Masalah yang Dipecahkan

- Organisasi perlu layaman identitas terpusat untuk mengelola pengguna dan akses ke sistem lain.
- Kebutuhan kontrol akses berbasis peran (RBAC) dengan audit trail yang dapat diverifikasi.
- Kebutuhan manajemen sesi per perangkat agar satu perangkat bisa dicabut tanpa mematikan yang lain.
- Kebutuhan deteksi pemakaian ulang token refresh sebagai indikasi pencurian token.
- Kebutuhan visibilitas lonjakan penolakan 401 (401 aggregate) setelah instance restart.
- Kebutuhan monitoring dan alerting berbasis Prometheus untuk operasional production.
- Cegah penyalahgunaan token sah lintas tenant: header `X-Organization-ID` dikontrol pemanggil, sementara klaim `tenant_id` pada token tidak pernah diverifikasi → token tenant A bisa dipakai beroperasi di tenant B.
- Cegah refresh token (TTL 7 hari) dipakai sebagai Bearer credential ke endpoint terautentikasi (token_type tidak diverifikasi).
- Cegah kebocoran metadata lintas tenant pada verifikasi rantai audit publik-internal (audit chain bersifat global).

---

## 3. Cakupan Fitur (Package A / Fase 1–6 + Paket Hardening v1.1.0)

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
| Verifikasi rantai | `GET /v1/audit-logs/verify` melaporkan entri rusak pertama; sejak 2026-11-16 dibatasi untuk peran `OPS` (ADMIN/AUDITOR → 403) |
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

### 3.7 Multi-Tenancy (ADR-0016)

| Fitur | Detail |
|-------|--------|
| Isolasi data | Shared database/schema, kolom `tenant_id` di tabel inti (`users`, `sessions`, `refresh_tokens`, `audit_logs`, `security_rejection_stats`) |
| Organisasi | Setiap tenant memiliki `tenant_id` internal dan `organization_id` eksternal (header `X-Organization-ID`) |
| Header organisasi | `X-Organization-ID` diekspos ke klien; `TenantHeaderFilter` me-resolve ke `tenant_id` internal |
| Identitas tenant-scoped | Username dan email unik per tenant: `UNIQUE(tenant_id, username)` dan `UNIQUE(tenant_id, email)` (diputuskan 2026-11-14, menggantikan constraint unik global) |
| Kontrak login | `POST /v1/auth/login` dan `POST /v1/auth/refresh` **wajib** `X-Organization-ID` (breaking contract v1.0.0, 2026-11-14) |
| Binding token ↔ tenant | Pada semua endpoint terautentikasi kecuali login/refresh, klaim `tenant_id` pada access token harus sama dengan tenant hasil resolusi header; mismatch → 401 (`tenant_mismatch`) — diadopsi 2026-11-16 (ADR-0019) |
| Peran | Tabel `roles` tetap global (`ADMIN`, `AUDITOR`, `USER`, `MANAGER`, `OPS`) |
| On-premise | Satu tenant `org-default`; header boleh default ke sana |
| Cloud | Banyak tenant dalam satu deployment bersama |

### 3.8 Structured Logging (ADR-0017)

| Fitur | Detail |
|-------|--------|
| Lima kategori | `errors` (galat/kegagalan), `generals` (peristiwa umum), `integrations` (integrasi eksternal), `timers` (durasi operasi), `emails` (pengiriman email — belum aktif, tetap dispesifikasikan) |
| Format | Structured logging ke stdout, satu event per baris; trace/span id diset via MDC |
| Tenant | Setiap entry membawa `tenant_id` bila tersedia |
| Retensi | Dikelola oleh platform log (mis. Loki/ELK), bukan di aplikasi |

### 3.9 Operasional & Deployment

| Fitur | Detail |
|-------|--------|
| Docker multi-stage | JRE 21, user non-root 10001:10001, profil prod |
| Backup/restore | Skrip bash dengan `pg_dump`/`pg_restore`, mode container |
| Uji restore CI | `.github/workflows/restore-drill.yml` cadangkan → hapus → pulihkan → verifikasi |
| CODEOWNERS | Pemilik untuk seluruh repo dan path sensitif |
| cspell | Konfigurasi ejaan untuk dokumentasi Bahasa Inggris |

---

### 3.10 Paket Hardening Keamanan (v1.1.0, 2026-11-16)

| Fitur | Detail |
|-------|--------|
| Binding token ↔ tenant | Klaim `tenant_id` pada access token wajib sama dengan tenant hasil resolusi `X-Organization-ID` pada setiap endpoint terautentikasi kecuali `/v1/auth/login` dan `/v1/auth/refresh` (refresh menambatkan ke tenant tersimpan, ADR-0018 poin 6); mismatch → 401 `tenant_mismatch` (ADR-0019) |
| Jenis token | Resource server menolak token dengan `token_type` selain `access`; refresh token (TTL 7 hari) tidak dapat dipakai sebagai Bearer |
| Verifikasi rantai audit → OPS | `GET /v1/audit-logs/verify` dibatasi peran `OPS`; ADMIN/AUDITOR → 403. Peran global `OPS` ditambahkan oleh migrasi V12 (tidak diberikan ke user mana pun otomatis); akun operasional dibuat manual per deployment |
| Retensi agregat 401 menyeluruh | Pembersihan `security_rejection_stats` kini berlaku untuk semua tenant (sebelumnya hanya `org-default` akibat fallback pada thread penjadwal) |
| HTTPS in-app | TLS terminated di aplikasi (`server.ssl.*`), tanpa reverse proxy; sertifikat dari CA internal (`Osprey Internal CA`), SAN `DNS:iam.osprey.service`, valid hingga 2029-02-18; port 9090 hanya melayani HTTPS (request HTTP → 400) |
| DNS & trust store | Klien harus resolve `iam.osprey.service` → host (DNS override router) dan mempercayai CA internal (import root cert sekali per klien) |
| Konteks tenant tanpa warisan thread | `TenantContext` tidak lagi diwariskan ke child thread (`InheritableThreadLocal` → `ThreadLocal`) |
| Penolakan header tercatat | 401 missing/unknown organisasi dicatat ke log WARN, metrik `iam.security.rejected`, dan agregat per menit (sebelumnya tidak tercatat) |

## 4. Batasan yang Diketahui (Package A tidak mencakup)

- Rate limit terdistribusi (hanya per instance)
- Pencabutan access token secara langsung (access token berlaku hingga kadaluwarsa)
- WORM / penyimpanan append-only eksternal untuk jejak audit
- Integrasi SAML atau OIDC
- Pendaftaran mandiri (self-registration)

Multi-tenancy dan structured logging **sudah masuk ruang lingkup** pada 2026-11-14 (diimplementasikan lewat ADR-0016 dan ADR-0017). Paket hardening keamanan (§3.10) masuk ruang lingkup pada 2026-11-16 (ADR-0019, migrasi V12, TLS).

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
- [ ] Identitas tenant-scoped: `UNIQUE(tenant_id, username)` dan `UNIQUE(tenant_id, email)` pada database target
- [ ] `POST /v1/auth/login` dan `POST /v1/auth/refresh` menolak permintaan tanpa `X-Organization-ID` (400/401)
- [ ] Isolasi lintas tenant terverifikasi otomatis (pengguna tenant A tidak melihat data tenant B)
- [ ] Binding token ↔ tenant: request dengan token tenant A + header `X-Organization-ID: tenant-b` ditolak **401 `tenant_mismatch`** (ADR-0019, `TenantBindingIntegrationTest`)
- [ ] Resource server menolak token dengan `token_type` selain `access` (refresh token tidak bisa dipakai sebagai Bearer)
- [ ] `GET /v1/audit-logs/verify` → **403** untuk `ADMIN`/`AUDITOR`, **200** untuk `OPS` (V12)
- [ ] Migrasi **V12** (seed role `OPS`) tercatat sukses di `flyway_schema_history`
- [ ] HTTPS aktif di production; request HTTP plain ditolak (400)
- [ ] Isolasi tenant pada verify: hanya akun `OPS` yang dapat membaca verifikasi rantai lintas tenant

## 6. Rilis

Versi saat ini: **`1.1.0`** (2026-11-16) — Package A + **Paket Hardening Keamanan** (§3.10):
binding klaim `tenant_id` ↔ tenant hasil resolusi header (ADR-0019), penegakan `token_type=access`,
pembatasan `GET /v1/audit-logs/verify` ke peran `OPS` (V12), dan HTTPS in-app (CA internal,
`iam.osprey.service`). Deployed ke host LAN 2026-11-16 dengan rollback exercise terbukti
(1.1.0 → 1.0.1 → 1.1.0). Grade rilis: **A-** (`docs/07-DEPLOYMENT.md` §12).

Riwayat rilis:
- `1.0.0` (2026-11-14) — Paket A (Fase 1–6) + multi-tenancy ADR-0016/0018 + structured logging ADR-0017
- `1.0.1` (2026-11-14/15) — perbaikan BUG-TENANT-005 + dependensi CI
- `1.1.0` (2026-11-16) — Paket Hardening Keamanan (ADR-0019, V12, TLS)

PRD ini sebelumnya tertinggal (masih menyebut `0.1.0` dan `[Belum Dirilis]`) — diperbarui pada 2026-11-14,
dan diperbarui lagi pada **2026-11-16** agar sinkron dengan v1.1.0 (`CHANGELOG.md`, `ADR-0019`, `10-PENTEST.md`).

Penambahan scope berikutnya (selain Paket A/v1.1.0) hanya boleh masuk lewat revisi PRD ini
atau ADR baru — bukan dilaksanakan diam-diam di bawah label "perbaikan".

## 7. Pertanyaan Terbuka

- [PERLU KONFIRMASI] Peran OPS & akun operasional: apakah deployment lain juga butuh akun `OPS` sendiri, dan siapa yang berhak men-generate akun tersebut (prosedur sudah di `docs/07-DEPLOYMENT.md` §13)?
- [PERLU KONFIRMASI] Self-registration tetap di luar scope atau masuk roadmap (butuh keputusan tertulis)?
- [TERBUKA] Multi-organisasi dalam satu akun (mis. 1 user = 2 org) — belum ada; saat ini model "1 akun = 1 tenant" (ADR-0018). Bila dibutuhkan, perlu ADR baru (opsi membership `tenant_users`, ADR-0019 Alternative B).
- [PERLU KONFIRMASI] Browser CORS untuk klien frontend publik: origin mana yang akan dipakai di produksi dan apakah akan dilayani dari domain `iam.osprey.service` yang sama? (`CORS_ALLOWED_ORIGINS` di-host di `secrets\.env`, bukan di repo.)
