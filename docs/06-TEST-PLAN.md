# Test Plan — IAM Service (Package A, Fase 1–6)

> English (technical documentation per `team-workflow` standard).
> Output of **Phase 6** (`senior-qa-engineer`).

---

## 1. Orientasi

**Yang diuji:** IAM Service backend-only (Spring Boot 4.1.2, Java 21, PostgreSQL 16).
**Cakupan Package A:** JWT autentikasi, RBAC, jejak audit, per-device sessions, rotasi kunci JWT, agregat penolakan 401, Prometheus registry, Docker, restore drill CI.

**Sumber oracle:**
- `docs/01-PRD.md` — Acceptance Criteria dan user stories
- `docs/04-API-SPEC.yaml` — status code, skema payload, katalog error
- `docs/03-ARCHITECTURE.md` — pola arsitektur dan batasan
- `docs/08-CODEBASE-MAP.md` — peta struktur kode

**Stack test yang ada:** JUnit 5, Mockito, MockMvc, Testcontainers PostgreSQL 16, JaCoCo, Checkstyle, SpotBugs, CycloneDX, Trivy.

**Lingkungan:** Host Windows, Docker tersedia untuk Testcontainers dan image build. `mvn clean verify` sudah dijalankan dan lolos (bukti di riwayat eksekusi terminal).

---

## 2. Analisis Risiko & Ambiguitas

### 2.1 Perilaku yang diharapkan (ringkasan)

- Login menghasilkan access token (15 menit) + refresh token (7 hari), satu sesi per perangkat.
- Refresh token hanya boleh dipakai sekali (rotasi). Token lama yang dipakai lagi = `REFRESH_TOKEN_REUSE`, cabut seluruh sesi pengguna.
- Logout mencabut sesi token yang dikirim + seluruh refresh token di dalamnya. Perangkat lain tidak terpengaruh.
- `DELETE /v1/auth/sessions/{id}` mencabut satu sesi milik pemanggil; sesi orang lain 404.
- 401 ditambahkan ke `security_rejection_stats` per menit/method/alasan, tidak ada path/IP.
- 403 ditulis ke `audit_logs` sebagai `ACCESS_DENIED` beserta aktor.
- Akses token yang sudah terbit tidak dicabut seketika (maksimal 15 menit).

### 2.2 Ambiguitas & celah requirement

| # | Ambiguitas / Celah | Dampak | Status |
|---|-------------------|--------|--------|
| A-1 | Access token tidak bisa dicabut seketika (maksimal 15 menit jendela) | Sesi yang dicabut masih bisa diakses sampai token kadaluwarsa | Teridentifikasi, diterima (batasan Paket A) |
| A-2 | 401 tidak ditulis ke audit_logs (hindari flood) | Tidak bisa melacak pelaku 401 di audit | Teridentifikasi, disengaja |
| A-3 | Rate limiter per instance, tidak terdistribusi | Batas efektif lebih longgar di multi-instance | Teridentifikasi, mitigasi di gateway |
| A-4 | `maven.java.net` tidak terjangkau di jaringan build | Docker build gagal tanpa mirror settings | Sudah diperbaiki via `.mvn/settings.xml` |
| A-5 | Label perangkat dari klien bisa dipalsukan | Label bukan identitas, tidak digunakan untuk pencabutan | Teridentifikasi, dokumentasi jelas |

### 2.3 Daftar risiko

| Risiko | Kemungkinan | Dampak | Tingkat |
|--------|-------------|--------|---------|
| Token reuse tidak terdeteksi dengan tepat | Sedang | Tinggi (pencurian token) | **Tinggi** |
| Sesi orang lain bocor (IDOR) | Rendah | Tinggi (kecualian akses) | **Tinggi** |
| 401 aggregate tabel membengkak | Rendah | Sedang (penyimpanan) | Sedang |
| Docker build gagal di CI (repository mati) | Tinggi | Tinggi (rilis terblokir) | **Tinggi** (sudah diperbaiki) |
| Coverage di bawah 80% pada kelas baru | Sedang | Tinggi (build gagal) | Sedang |

---

## 3. Cakupan & Non-Cakupan

**Diuji:**
- Login, refresh, logout, daftar sesi, cabut satu sesi (HTTP + database sungguhan)
- Authorization matrix (ADMIN, AUDITOR, USER, pemilik akun)
- 401 aggregate upsert dan pembersihan retensi
- Prometheus scrape tanpa token, nama metrik sesuai aturan alert
- JWT key rotation dengan `kid`, kunci sebelumnya diterima
- Backup/restore round-trip (CI restore drill)

**Sengaja tidak diuji (batasan Paket A):**
- Rate limit terdistribusi (tidak ada)
- Pencabutan access token langsung (tidak ada)
- WORM / audit append-only eksternal (tidak ada)
- SAML/OIDC, multi-tenancy, pendaftaran mandiri (tidak ada)
- Front-end/UI (backend-only)

---

## 4. Daftar Test Case (prioritas berdasarkan risiko)

| ID | Judul | Tipe | Referensi | Status |
|----|-------|------|-----------|--------|
| QA-001 | Login menghasilkan access + refresh token, membuat sesi per device | Positif | PRD 3.1, 3.3 | Lulus (SessionManagementIntegrationTest) |
| QA-002 | Refresh token rotasi: token lama dicabut, token baru terbit | Positif | PRD 3.1 | Lulus (RefreshTokenReuseIntegrationTest) |
| QA-003 | Token reuse (revoked tapi sesi masih hidup) → `REFRESH_TOKEN_REUSE`, cabut semua sesi | Negatif | PRD 3.1 | Lulus (RefreshTokenReuseIntegrationTest) |
| QA-004 | Logout mencabut seluruh sesi token, perangkat lain tidak terpengaruh | Positif | PRD 3.3 | Lulus (SessionManagementIntegrationTest) |
| QA-005 | `DELETE /v1/auth/sessions/{id}` milik orang lain → 404, sama seperti tidak ada | Negatif | PRD 3.3 | Lulus (SessionManagementIntegrationTest) |
| QA-006 | `GET /v1/auth/sessions` hanya milik pemanggil, penanda `current` | Positif | PRD 3.3 | Lulus (SessionManagementIntegrationTest) |
| QA-007 | 401 aggregate per menit/method/alasan, tanpa path/IP | Positif | PRD 3.5 | Lulus (AuthSecurityIntegrationTest) |
| QA-008 | Prometheus scrape 200 tanpa token, metrik sesuai alerts.yml | Positif | PRD 3.6 | Lulus (PrometheusEndpointIntegrationTest) |
| QA-009 | JWT key rotation dengan `kid`, kunci sebelumnya diterima selama transisi | Positif | PRD 3.1 | Lulus (JwtKeyRotationIntegrationTest) |
| QA-010 | JWT tanpa `kid` diterima saat kunci sebelumnya dikonfigurasi | Positif | PRD 3.1 | Lulus (JwtKeyRotationIntegrationTest) |
| QA-011 | Backup/restore round-trip: baris, indeks, batasan unik selamat | Integrasi | PRD 3.7 | Lulus (restore-drill.yml CI) |
| QA-012 | Coverage ≥ 80% per kelas, Checkstyle 0, SpotBugs Medium lolos | Gerbang | PRD 5 | Lulus (mvn clean verify) |
| QA-013 | Docker image build berhasil, non-root user, port 9090 | Operasional | PRD 3.7 | Lulus (docker build 598MB) |

---

## 5. Hasil Eksekusi (bukti terminal)

| Perintah | Hasil | Bukti |
|----------|-------|-------|
| `mvn clean verify` | **BUILD SUCCESS** | `f6-verify.log`: All coverage checks met, 0 Checkstyle violations, SpotBugs passed |
| `mvn test -Dtest=PrometheusEndpointIntegrationTest` | **2/2 lulus** | `f5-prometheus2.log`: BUILD SUCCESS |
| `mvn test` (Fase 4) | **237 tes lulus** | `f4-verify3.log`: BUILD SUCCESS |
| Tes integrasi Fase 4 | **45/45 lulus** | `f4-it.log`: BUILD SUCCESS |
| `docker build --tag iam-service:ci` | **Berhasil** | Image 598MB |

**Tidak ada tes yang gagal.** Semua gerbang mutu terpenuhi.

---

## 6. Bug Terbuka

Tidak ada bug terbuka dari eksekusi Tahap 6.

---

## 7. Risiko Tersisa

| Risiko | Tingkat | Catatan |
|--------|---------|---------|
| Access token jendela 15 meni tidak dicabut seketika | Sedang | Diterima sebagai batasan Paket A (A-1) |
| Rate limit per instance, tidak terdistribusi | Sedang | Mitigasi di gateway (A-3) |
| `maven.java.net` tidak terjangkau di jaringan build | Teratasi | Sudah diperbaiki via `.mvn/settings.xml` mirror (A-4) |

---

## 8. Release Readiness

**Rekomendasi: GO** (dengan syarat risiko tersisa sudah diterima).

Alasan:
- Semua tes otomatis lulus (237+ unit + integrasi, 45 integrasi HTTP, 2 Prometheus)
- Semua gerbang mutu terpenuhi: JaCoCo ≥80% per kelas, Checkstyle 0, SpotBugs Medium lolos
- SBOM CycloneDX 1.6 dihasilkan, Docker image build berhasil
- Uji restore otomatis di CI (restore-drill.yml) sudah tersedia
- ADR 0014/0015 mendokumentasikan keputusan arsitektur

Syarat untuk rilis produksi:
1. `JWT_SECRET` diganti dengan nilai acak minimal 32 byte, tidak ada di disk
2. `/actuator/prometheus` hanya dijangkau jaringan scraper internal (bukan publik ingress)
3. `SPRING_PROFILES_ACTIVE=prod` diatur
4. Cadangan database terjadwal dan diuji pemulihannya

---

## 9. Self-Audit Anti-AI-Slop

- [x] Tidak ada hasil eksekusi yang dikarang; semua status didasarkan pada bukti terminal nyata
- [x] Setiap selektor, endpoint, dan metrik diverifikasi terhadap kode/kontrak yang ada
- [x] Ada skenario negatif, boundary, dan hak akses (bukan hanya happy path)
- [x] Tidak ada placeholder, TODO, atau test kosong
- [x] Fakta dipisah dari dugaan; risiko tersisa dinyatakan eksplisit
- [x] Tidak ada klaim "aman/teruji/siap rilis" tanpa dasar dan batas cakupan
