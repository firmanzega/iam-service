# Changelog

Semua perubahan penting pada proyek ini dicatat di berkas ini.

Format-nya mengikuti [Keep a Changelog](https://keepachangelog.com/id/1.1.0/),
dan proyek ini memakai [semantik versi](https://semver.org/lang/bahasa/).

---

## [1.0.1] - 2026-11-14

Rilis pemeliharaan: **tidak ada perubahan kontrak** (non-breaking). Memuat perbaikan keamanan BUG-TENANT-005 dan pembaruan dependensi CI.

### Diperbaiki
- `/v1/audit-logs` dan `/v1/audit-logs/verify` kini mewajibkan `X-Organization-ID`; header yang hilang ditolak **401 Unauthorized** (sebelumnya 200 dengan halaman kosong karena kueri dibatasi `tenant_id` null) — selaras ADR-0018 (BUG-TENANT-005)
- Pencatatan audit tanpa konteks tenant (mis. endpoint global `/v1/roles`) kini tercatat WARN saat dikaitkan ke `org-default` (sebelumnya diam-diam)

### Diperbarui — dependensi CI (tidak memengaruhi artefak runtime)
- `actions/checkout` 4 -> 7, `actions/setup-java` 4 -> 6, `actions/upload-artifact` 4 -> 7
- `org.jacoco:jacoco-maven-plugin` 0.8.13 -> 0.8.16

### Dokumentasi
- API-SPEC memetakan seluruh endpoint `/v1` (auth, users, roles, audit-logs, verify): 16 path, 20 operasi
- Rubric grading rilis ditambahkan ke `docs/07-DEPLOYMENT.md` §12; grade pada rilis ini: **B+**

### Kode
- `WebMapper.tenantId` dihapus (dead code; otorisasi tenant berbasis header per ADR-0018)

---

## [1.0.0] - 2026-11-14

Rilis pertama stabil: Package A selesai (Fase 1–6) dan seluruh perluasan multi-tenancy, layanan identitas dan akses siap produksi dengan kontrol keamanan berlapis.

**Perhatian — breaking contract dibanding 0.1.0:** lihat bagian "Breaking Contract" di bawah. Klien wajib mengirim `X-Organization-ID` pada endpoint yang memuat tenant, dan identitas pengguna tidak lagi global unik.

### Ditambahkan
- Kontrak API berversi di bawah `/v1`, perubahan yang tidak kompatibel dapat diterbitkan sebagai `/v2`
- Sesi per perangkat: setiap login buat baris `sessions`, token membawa klaim `session_id`, `GET /v1/auth/sessions` dan `DELETE /v1/auth/sessions/{id}`
- Label perangkat opsional pada login (`deviceLabel`, maksimal 100 karakter, alat bantu pemilik akun bukan identitas)
- Agregat penolakan 401 per menit, method, dan alasan di `security_rejection_stats`, tanpa path dan alamat IP
- Rotasi kunci JWT dengan penanda `kid` pada header, `JWT_SECRET_PREVIOUS` selama masa transisi
- Rantai hash pada `audit_logs` beserta endpoint `GET /v1/audit-logs/verify`
- Deteksi pemakaian ulang token refresh: cabut seluruh sesi pengguna, catat `REFRESH_TOKEN_REUSE`
- Registry Prometheus (`micrometer-registry-prometheus`), endpoint `/actuator/prometheus`, aturan alert di `ops/prometheus/alerts.yml`
- Uji restore otomatis di GitHub Actions (`.github/workflows/restore-drill.yml`)
- `CODEOWNERS` untuk seluruh repositori dan path sensitif keamanan
- Gerbang mutu: JaCoCo ≥80% per kelas, Checkstyle 0, SpotBugs Medium, SBOM CycloneDX 1.6, Trivy image scan

### Ditambahkan — Multi-tenancy (ADR-0016, ADR-0017, ADR-0018)
- Shared schema: kolom `tenant_id` pada `users`, `sessions`, `refresh_tokens`, `audit_logs`, `security_rejection_stats`; tabel `tenants` dengan `organization_id` eksternal (`X-Organization-ID`) dihadapkan ke klien
- Identitas tenant-scoped: `UNIQUE(tenant_id, username)` dan `UNIQUE(tenant_id, email)` menggantikan batasan global unik (V10); menyimpan `admin` di dua organisasi berbeda pada satu instalasi adalah keadaan yang sah
- `POST /v1/auth/login` dan `POST /v1/auth/refresh` wajib `X-Organization-ID`; header hilang atau organisasi tidak dikenal → **401 Unauthorized** di seluruh endpoint tenant-aware
- Structured logging berdasarkan kategori (errors/generals/integrations/timers/emails) dengan MDC `tenantId` dan `traceId` (ADR-0017)
- Agregat 401 tenant-scoped: `UNIQUE(tenant_id, window_start, http_method, reason)` (V11) dengan satu statement upsert

### Breaking Contract
- Klien wajib mengirim `X-Organization-ID` pada `POST /v1/auth/login` dan `POST /v1/auth/refresh`; tanpa header → 401 Unauthorized
- Klien wajib mengirim `X-Organization-ID` pada seluruh endpoint `/v1/users/*` (GET/POST/PUT/PATCH termasuk self-service password); tanpa header atau organisasi tidak dikenal → 401 Unauthorized
- Identitas pengguna tidak lagi global unik: `username` dan `email` unik per tenant; aplikasi yang mengandalkan keunikan global harus migrasi data sebelum upgrade
- Organisasi tidak dikenal ditolak 401 di seluruh endpoint tenant-aware (sebelumnya organisasi tidak dikenal dapat jatuh ke `org-default` pada jalur tertentu)
- Deployment on-premise: klien mengirim `X-Organization-ID: org-default` sesuai konfigurasi deploy; cloud: klien mengirim organization id-nya

### Diperbaiki
- `AuthService.revokeSession` tidak memanggil pencabutan token dua kali; `ifPresentOrElse` memastikan token tetap dicabut bila baris sesi sudah hilang
- Docker image build: mirror repository Maven ke Maven Central via `.mvn/settings.xml` (maven.java.net tidak terjangkau dari jaringan build)
- SpotBugs pengecualian `EI_EXPOSE_REP2` per kelas dan pola, tidak mematikan pola secara global
- Login lookup bocor lintas tenant: derived query `findByUsernameOrEmail` mengabaikan `tenantId` → predikat tenant eksplisit (BUG-TENANT-004)
- Organisasi tidak dikenal jatuh diam-diam ke `org-default` → kini ditolak 401 di seluruh path (BUG-TENANT-001)
- Agregat 401 tenant non-default hilang diam-diam: constraint global `uq_security_rejection_stats` dilanggar insert tenant kedua pada menit yang sama → di-scope menjadi komposit tenant-aware (SEC-STATS-001, V11)
- Pori cakupan JaCoCo per kelas yang menahan gerbang `mvn clean verify`: kelas logging, tenant, dan `WebMapper` kini terkover; `mvn clean verify` BUILD SUCCESS (299/299)

### Batasan yang Diketahui
- Rate limit terdistribusi, pencabutan access token langsung, WORM eksternal, SAML/OIDC, pendaftaran mandiri
- Access token tidak dicabut seketika (maksimal 15 menit jendela)
- Rate limiter per instance, mitigasi di gateway
- 401 tidak ditulis ke audit_logs (hindari flood oleh pemanggil tidak teridentifikasi)
- Rollback V11 hanya aman sebelum onboarding tenant kedua (lihat `docs/07-DEPLOYMENT.md` §7)

---

## [Belum Dirilis]

Belum ada — seluruh perubahan sudah masuk ke rilis [1.0.1].

---

## [0.1.0] - 2026-11-12

Versi pertama yang lengkap: layanan identitas dan akses dengan autentikasi,
kontrol akses berbasis peran, jejak audit, dan dokumentasi.

### Ditambahkan

**Autentikasi**
- Token JWT HS256 dengan access token 15 menit dan refresh token 7 hari
- Penyimpanan refresh token sebagai hash SHA-256
- Rotasi refresh token, token lama langsung dicabut setelah ditukar
- Pencabutan seluruh sesi saat ganti kata sandi, ganti role, atau nonaktifkan akun
- Login dengan username maupun email
- Kunci akun otomatis setelah lima kali gagal, dihitung per akun
- Penolakan login dengan pesan identik untuk username salah dan password salah
- Validasi penerbit token

**Kontrol akses**
- Aturan per URL dan per method untuk endpoint pengguna, role, dan jejak audit
- Daftar pengguna hanya untuk peran ADMIN dan AUDITOR
- Hanya pemilik akun yang boleh mengganti kata sandinya sendiri

**Manajemen peran**
- Pembuatan dan penghapusan role
- Penggantian seluruh role milik seorang pengguna

**Jejak audit**
- Catatan untuk pembuatan pengguna, perubahan data, penggantian role pengguna,
  pembuatan dan penghapusan role, aktivasi, penonaktifan, penggantian kata
  sandi, login, logout, dan penukaran token refresh
- Akses baca jejak audit untuk peran ADMIN dan AUDITOR

**Lapisan arsitektur**
- Pemisahan menjadi lapisan domain, application, infrastructure, dan adapter
- Repository sebagai port di domain dengan adapter di infrastruktur
- Entitas domain tanpa anotasi JPA
- Galat domain dengan kategori, dipetakan ke status HTTP di lapisan adapter
- Pengujian otomatis yang memeriksa arah dependensi antar lapisan

**Basis data**
- Migrasi Flyway sebagai satu sumber kebenaran skema
- Tabel refresh token dan jejak audit
- Indeks kunci asing, indeks trigram untuk pencarian, dan indeks status aktif
- Data contoh pada lokasi migrasi terpisah yang hanya dipindai di profil dev

**Dokumentasi**
- README lengkap dengan konfigurasi, daftar endpoint, dan struktur proyek
- Delapan catatan keputusan arsitektur
- Panduan deployment dan manajemen secret
- Panduan uji manual dengan contoh keluaran sungguhan
- Koleksi permintaan HTTP siap pakai

**Operasional**
- Endpoint kesehatan dan metrik lewat Spring Boot Actuator
- Dokumentasi OpenAPI dengan Swagger UI, dimatikan di profil produksi
- Alur kerja otomatis di GitHub Actions
- Pemeriksaan cakupan kode dengan ambang 80 persen

### Diperbaiki
- Batas ukuran halaman yang diam-diam diabaikan karena anotasi
  `@EnableSpringDataWebSupport`
- Kegagalan pengujian di mesin baru karena berkas `.env` tidak dibaca otomatis
- Profil produksi yang gagal start karena tidak ada konfigurasi datasource
- Pengurutan pada kolom hash kata sandi yang diizinkan
- Pengujian yang meninggalkan data sampah di database
- Deadlock saat dua pembaruan pengguna berjalan bersamaan
- Halaman Swagger UI yang tampil kosong putih

[Belum Dirilis]: https://github.com/firmanzega/iam-service/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/firmanzega/iam-service/releases/tag/v1.0.0
[0.1.0]: https://github.com/firmanzega/iam-service/releases/tag/v0.1.0
