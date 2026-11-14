# Changelog

Semua perubahan penting pada proyek ini dicatat di berkas ini.

Format-nya mengikuti [Keep a Changelog](https://keepachangelog.com/id/1.1.0/),
dan proyek ini memakai [semantik versi](https://semver.org/lang/bahasa/).

---

## [Belum Dirilis]

Perubahan yang sudah masuk ke branch `main` tapi belum ada rilis bertanda.

### Ditambahkan
- Dokumentasi library yang dipakai beserta alasan dan versinya
- Peta kode yang menunjukkan peran setiap berkas
- Panduan kontribusi berisi konvensi penamaan dan alur review
- Panduan hari pertama untuk developer baru
- Kamus istilah yang dipakai di kode
- Templat issue dan templat pull request
- Dokumen model keamanan beserta batas-batasnya
- Kebijakan pelaporan kerentanan
- Pemeriksaan cakupan kode otomatis dengan ambang 80 persen
- Alur kerja otomatis di GitHub Actions
- Dokumentasi OpenAPI dengan deskripsi Bahasa Indonesia pada setiap endpoint
- Jejak audit untuk seluruh perubahan penting
- Jejak audit `TOKENS_REVOKED` pada setiap pencabutan token refresh massal,
  memuat aktor pemanggil dan jumlah token yang dicabut
- Catatan keputusan arsitektur kesembilan tentang perbaikan komentar migrasi
  terapan lewat repair checksum
- Pembersihan otomatis token refresh yang sudah kedaluwarsa setiap jam, beserta
  pengaturan selang dan sakelar lewat `application.yaml`
- Pemindaian berkala entri rate limiter yang sudah tidak aktif, supaya memori
  tidak tumbuh mengikuti jumlah alamat IP yang pernah memanggil
- Catatan keputusan arsitektur kesepuluh tentang pemeliharaan berkala di dalam
  proses aplikasi
- Deteksi pemakaian ulang token refresh: seluruh sesi pengguna dicabut dan
  kejadiannya dicatat sebagai `REFRESH_TOKEN_REUSE`
- Catatan keputusan arsitektur kesebelas tentang deteksi pemakaian ulang token
  refresh
- Database pengujian terisolasi memakai Testcontainers PostgreSQL 16, dibuat
  sekali per proses dan tidak menyentuh database pengembangan
- Jejak audit `ACCESS_DENIED` untuk penolakan 403 beserta pelakunya, log WARN
  untuk 401 dan 403, serta metrik `iam.security.rejected` dengan tag `reason`
  dan `method`
- Tracing OpenTelemetry lewat OTLP/HTTP dengan sampling bawaan mati, dan
  `traceId`/`spanId` pada pola log saat tracing dinyalakan
- `Dockerfile` multi-stage dengan JRE 21 dan pengguna tanpa hak istimewa,
  beserta `.dockerignore`
- Build image pada GitHub Actions dan pemindaian dependensi pada pull request
  lewat `dependency-review-action`
- Checkstyle dan SpotBugs pada fase `verify`, dengan konfigurasi
  `config/checkstyle/checkstyle.xml`
- Skrip `scripts/backup-postgres.sh` dan `scripts/restore-postgres.sh` dengan
  mode container dan konfirmasi sebelum memulihkan
- Catatan keputusan arsitektur kedua belas tentang database pengujian
  terisolasi dan ketiga belas tentang observability penolakan serta tracing
- Kontrak API berversi di bawah `/v1`, sehingga perubahan yang tidak kompatibel
  bisa diterbitkan sebagai `/v2` tanpa memaksa pemanggil lama ikut berubah
- Gerbang mutu per kelas: cakupan baris minimal 80 persen untuk setiap kelas,
  Checkstyle, SpotBugs ambang Medium, SBOM CycloneDX 1.6, dan pemindaian Trivy
  pada image hasil build
- Rantai hash pada `audit_logs` beserta endpoint `GET /v1/audit-logs/verify`
  yang melaporkan entri rusak pertama dan alasannya
- Rotasi kunci token memakai penanda `kid` pada header, dengan
  `JWT_SECRET_PREVIOUS` menerima token lama selama masa transisi
- Sesi per perangkat: setiap login membuka baris `sessions`, token membawa klaim
  `session_id`, dan `GET /v1/auth/sessions` menampilkan daftar perangkat pemilik
  token
- Pencabutan satu sesi lewat `DELETE /v1/auth/sessions/{id}`; sesi milik
  pengguna lain dijawab 404 tanpa membedakannya dari sesi yang tidak ada
- Label perangkat opsional pada login sebagai alat bantu pemilik akun
- Agregat penolakan 401 per menit, method, dan alasan di
  `security_rejection_stats`, beserta tugas pembersihan berdasarkan retensi
- Registry Prometheus, aturan alert di `ops/prometheus/alerts.yml`, dan uji
  integrasi yang mengunci nama metrik yang dipakai aturan tersebut
- Uji restore otomatis di GitHub Actions yang membuat cadangan, menghapus data,
  memulihkannya, lalu memeriksa baris, indeks, dan batasan unik
- `CODEOWNERS` yang menunjuk pemilik untuk seluruh repositori dan untuk path
  sensitif keamanan

### Diperbaiki
- Alasan galat saat role yang masih dipakai hendak dihapus kini `ROLE_IN_USE`,
  bukan `DUPLICATE_RESOURCE`
- Urutan daftar jejak audit yang sebelumnya tidak dijamin: sekarang terbaru
  lebih dulu dengan `id` sebagai pemecah seri, dan properti sort asing ditolak
  dengan 400
- Kata rusak pada komentar migrasi V5, disertai sinkronisasi checksum Flyway di
  database pengembangan
- Duplikasi penerjemah pagination dan identitas pelaku di controller, serta
  pembacaan jejak audit yang melewati lapisan application
- Logger `AuthService` yang dibuat ulang pada setiap pemanggilan pencabutan token
- Teks rusak pada komentar kelas Java dan dokumentasi
- Rujukan dokumen yang menunjuk ke berkas yang tidak ada di repositori
- Konvensi penamaan pada panduan kontribusi yang bertentangan dengan kode, dan
  istilah artifact yang diterjemahkan di beberapa header tabel
- Klaim dokumentasi yang tidak sesuai kode: cakupan jejak audit, dampak
  pencabutan sesi, versi pustaka, akses dokumentasi OpenAPI, dan port profil
  produksi
- Koleksi `http/iam-service.http` yang tidak bisa dijalankan dari atas ke bawah
  dan memakai nama field yang salah pada permintaan ganti kata sandi
- Pembersihan token refresh kini memakai satu perintah hapus, bukan memuat
  setiap baris lalu menghapusnya satu per satu
- Pencabutan seluruh sesi kini memakai satu `UPDATE` yang menandai `revoked_at`,
  bukan derived delete yang menghapus jejak token
- Rate limiter kini membaca alamat klien asli saat layanan berada di belakang
  reverse proxy internal
- Penjadwal pembersihan token tidak lagi berjalan di lingkungan pengujian, dan
  dua tugas latar memakai thread terpisah
- Tabel konfigurasi README yang belum memuat pengaturan pembersihan token dan
  entri rate limiter
- Klaim dokumentasi yang tidak sesuai kenyataan: jumlah permintaan pada koleksi
  HTTP, akses Swagger UI, dan checklist akun administrator dev
- Batasan operasional yang belum tercatat: belum ada `Dockerfile`, pipeline
  tanpa pemindaian CVE, cakupan diukur gabungan, dan penolakan akses hanya
  masuk log DEBUG
- Impor yang tidak terpakai pada lima berkas utama dan satu berkas pengujian
- Workflow CI tidak lagi membutuhkan layanan PostgreSQL dan berkas `.env`
  buatan tangan; pengujian menyediakan databasenya sendiri dan memakai kunci
  JWT pengujian
- Dokumentasi yang masih menyatakan Testcontainers tidak dipakai, tes memakai
  database pengembangan, dan repositori belum punya `Dockerfile`
- Pencabutan sesi yang memanggil pencabutan seluruh token dua kali ketika baris
  sesinya masih ada, dan yang kehilangan pencabutan token ketika baris sesinya
  sudah terhapus lebih dulu

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

[Belum Dirilis]: https://github.com/firmanzega/iam-service/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/firmanzega/iam-service/releases/tag/v0.1.0
