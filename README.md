# IAM Service

Layanan **Identity and Access Management** untuk mengelola pengguna, role, dan akses ke
sistem lain. Menyediakan autentikasi berbasis token JWT, kontrol akses berbasis peran,
serta jejak audit untuk setiap perubahan penting.

---

## Daftar Isi

- [Arsitektur](#arsitektur)
- [Kebutuhan Sistem](#kebutuhan-sistem)
- [Menjalankan](#menjalankan)
- [Konfigurasi](#konfigurasi)
- [Daftar Endpoint](#daftar-endpoint)
- [Dokumentasi OpenAPI](#dokumentasi-openapi)
- [Pengujian](#pengujian)
- [Struktur Proyek](#struktur-proyek)
- [Catatan Keputusan Arsitektur](#catatan-keputusan-arsitektur)

---

## Arsitektur

Layanan ini memakai **Clean Architecture** dengan pemisahan tanggung jawab yang tegas:

```
+--------------------------------------------------------------+
|  ADAPTER REST                  id.osprey.iamservice.adapter  |
|  Controller, DTO, penangkap galat HTTP                      |
|  Menerjemahkan HTTP menjadi perintah use case               |
+-----------------------------+--------------------------------+
                              | Bergantung pada port interface
+-----------------------------v--------------------------------+
|  APPLICATION                                                 |
|  Use case: orkestrasi aturan bisnis, batas transaksi         |
|  Tidak tahu HTTP, tidak tahu database                        |
+-----------------------------+--------------------------------+
                              | Bergantung pada port interface
+-----------------------------v--------------------------------+
|  DOMAIN                                                     |
|  Entitas, aturan bisnis murni, port interface                |
|  Bebas dari dependensi framework                             |
+-----------------------------+--------------------------------+
                              ^
+-----------------------------+--------------------------------+
|  INFRASTRUCTURE                                             |
|  Spring Boot, Spring Security, Spring Data JPA, PostgreSQL   |
|  Mengimplementasikan port interface dari domain              |
+--------------------------------------------------------------+
```

**Prinsip yang dipegang:**

| Prinsip | Wujudnya di proyek ini |
|---|---|
| Domain bebas dependensi framework | `domain/user/User.java` tidak memakai anotasi JPA sama sekali, dan tidak mengimpor satu pun paket framework |
| Dependency inversion | `domain/user/UserRepository.java` adalah port, diimplementasikan `infrastructure/persistence/UserPersistenceAdapter` |
| Use case tidak tahu HTTP | `application/user/UserService.java` tidak pernah menyentuh `HttpServletRequest` |
| Galat bisnis tidak tahu HTTP | `DomainException` membawa alasan galat, pemetaan ke status HTTP ada di `adapter/rest/GlobalExceptionHandler` |
| Aturan bisnis di satu tempat | `User.deactivate()`, `User.registerFailedLogin()`, `User.isLocked()` |
| Controller tipis | Controller hanya memvalidasi, meneruskan, dan memetakan hasil ke DTO |
| Tipe paginasi milik domain | `domain/shared/PaginationRequest` dan `PageResult`, bukan `Pageable` milik Spring Data |
| Arah dependensi satu | Aturan diperiksa otomatis oleh `ArchitectureLayerTest` |

Pemeriksaan aturan dependensi dijalankan otomatis sebagai bagian dari rangkaian
pengujian (`ArchitectureLayerTest`).

### Lapisan keamanan

```
Permintaan
   |
   +-> RateLimitFilter        membatasi percobaan login per alamat IP
   +-> Spring Security Filter  validasi token JWT, aturan akses per URL
   +-> @PreAuthorize            aturan akses tingkat method (mis. ganti password sendiri)
   +-> Controller               validasi bentuk permintaan
   +-> Use Case                 aturan bisnis, transaksi
   +-> Adapter JPA              pemetaan ke PostgreSQL
```

---

## Kebutuhan Sistem

| Komponen | Versi | Keterangan |
|---|---|---|
| Java | 21 (LTS) | Dipasang terpisah, lalu arahkan `JAVA_HOME` ke JDK 21 |
| Maven | 3.10+ | Dipakai lewat Maven Wrapper (tidak perlu diinstal) |
| PostgreSQL | 16 | Jalankan lewat Docker Compose |
| Docker | - | Menjalankan PostgreSQL, pengujian integrasi (Testcontainers), dan membangun image layanan |

---

## Menjalankan

> **Perintah Maven Wrapper berbeda antara Windows dan Linux/macOS:**
>
> | Sistem | Perintah |
> |---|---|
> | **Windows** (PowerShell atau cmd) | `.\mvnw.cmd` |
> | **Linux dan macOS** | `./mvnw` |
>
> Di Windows, `./mvnw` tidak akan dikenali oleh PowerShell. Selalu pakai
> `.\mvnw.cmd`.

### 1. Siapkan berkas `.env`

```bash
cp .env.example .env
```

Isi `JWT_SECRET` dengan kunci acak sepanjang minimal 32 byte:

```bash
openssl rand -base64 48
```

Aplikasi menolak start kalau `JWT_SECRET` tidak diisi atau kurang dari 32 byte.
Jadi secret yang hilang tidak akan pernah membuat layanan berjalan dengan kunci
lemah atau tidak sengaja.

### 2. Jalankan database

```bash
docker compose up -d
```

PostgreSQL akan berjalan di port `5433`. Skema dan data awal dibuat otomatis oleh
migrasi Flyway saat aplikasi pertama kali dijalankan.

### 3. Jalankan aplikasi

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd spring-boot:run

# Linux dan macOS
./mvnw spring-boot:run
```

Aplikasi tersedia di <http://localhost:8080>.

Pada profil `dev`, satu akun administrator dibuat otomatis saat pertama kali dijalankan:

| Field | Nilai |
|---|---|
| Username | `admin` |
| Email | `admin@iam.local` |
| Kata sandi | `Admin1234` |

> **Kalau database pengembangan dipakai bersama, ganti kata sandinya setelah
> login pertama.** Akun ini hanya ada di profil `dev` dan tidak pernah aktif di
> produksi.

### 4. Cek cepat

```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin","password":"Admin1234"}'
```

### 5. Menjalankan lewat container

Repositori ini punya `Dockerfile` multi-stage. Tahap build memakai JDK 21,
sedangkan tahap jalan hanya memakai JRE 21 dan berjalan sebagai pengguna tanpa
hak istimewa (`10001:10001`). Image memakai profil `prod` secara bawaan,
sehingga akun administrator dev tidak pernah ikut aktif.

```bash
docker build -t iam-service:latest .

docker run --rm -p 9090:9090 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e POSTGRES_HOST=host.docker.internal \
  -e POSTGRES_PORT=5433 \
  -e POSTGRES_DB=db_osprey \
  -e POSTGRES_USER=user_osprey \
  -e POSTGRES_PASSWORD=ganti-dengan-password-anda \
  iam-service:latest
```

Di Linux, `host.docker.internal` perlu diganti dengan alamat gateway container
atau nama layanan database di jaringan Docker yang sama. Rincian deployment
dan batasannya ada di [`docs/deployment.md`](docs/deployment.md).

---

## Konfigurasi

Semua kredensial dan parameter sensitif dibaca dari environment variable. Berkas `.env`
dibaca otomatis oleh Spring Boot lewat `spring.config.import`, sehingga **tidak perlu
export manual** untuk menjalankan aplikasi maupun pengujian.

| Variabel | Wajib | Keterangan |
|---|---|---|
| `POSTGRES_DB` | ya | Nama database |
| `POSTGRES_USER` | ya | Pengguna database |
| `POSTGRES_PASSWORD` | ya | Kata sandi database |
| `POSTGRES_HOST` | tidak | Bawaan `localhost` |
| `POSTGRES_PORT` | tidak | Bawaan `5433` |
| `JWT_SECRET` | ya | Kunci penanda tangan token, minimal 32 byte |
| `CORS_ALLOWED_ORIGINS` | tidak | Origin frontend yang diizinkan, pisahkan dengan koma |
| `OTEL_TRACES_SAMPLING_PROBABILITY` | tidak | Peluang sampling tracing, bawaan `0.0` (mati) |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | tidak | Alamat collector OTLP/HTTP, bawaan `http://localhost:4318/v1/traces` |

Parameter lain ada di `src/main/resources/application.yaml` pada blok `iam`:

| Properti | Bawaan | Keterangan |
|---|---|---|
| `iam.jwt.access-token-ttl` | `15m` | Masa berlaku token akses |
| `iam.jwt.refresh-token-ttl` | `7d` | Masa berlaku token refresh |
| `iam.login.max-failed-attempts` | `5` | Batas kegagalan login sebelum akun terkunci |
| `iam.login.lock-duration` | `15m` | Lama akun terkunci |
| `iam.rate-limit.max-requests` | `20` | Batas permintaan per jendela waktu pada endpoint autentikasi |
| `iam.rate-limit.window` | `1m` | Panjang jendela rate limit |
| `iam.rate-limit.cleanup-interval` | `10m` | Selang pemindaian entri rate limiter yang sudah tidak aktif |
| `iam.refresh-token-cleanup.enabled` | `true` | Sakelar pembersihan token refresh kedaluwarsa |
| `iam.refresh-token-cleanup.interval` | `1h` | Selang pembersihan token refresh kedaluwarsa |

Kalau layanan berjalan di belakang reverse proxy, `server.forward-headers-strategy`
sudah diatur ke `native`. Container hanya memakai `X-Forwarded-For` bila
koneksi datang dari proxy di jaringan internal, sehingga rate limiter melihat
alamat klien yang sebenarnya. Syarat dan konsekuensinya ada di
[`docs/deployment.md`](docs/deployment.md).

---

## Daftar Endpoint

Semua endpoint berada di bawah `/v1`, memakai JSON, dan memakai format galat yang sama:

```json
{
  "status": 409,
  "error": "Conflict",
  "timestamp": "2026-01-01T10:00:00",
  "path": "/v1/users",
  "message": "Username already exists",
  "details": {}
}
```

### Autentikasi

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| POST | `/v1/auth/login` | Publik | Terima token akses dan token refresh |
| POST | `/v1/auth/refresh` | Publik | Tukar token refresh (token lama dicabut) |
| POST | `/v1/auth/logout` | Terautentikasi | Cabut token refresh milik pemanggil |

### Pengguna

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/v1/users` | ADMIN, AUDITOR | Daftar pengguna, mendukung pencarian dan pengurutan |
| GET | `/v1/users/{id}` | ADMIN, AUDITOR, atau pemilik akun | Ambil satu pengguna |
| POST | `/v1/users` | ADMIN | Buat pengguna baru |
| PUT | `/v1/users/{id}` | ADMIN | Ganti seluruh data pengguna |
| PATCH | `/v1/users/{id}` | ADMIN | Ubah sebagian data pengguna |
| POST | `/v1/users/{id}/activate` | ADMIN | Aktifkan kembali pengguna |
| POST | `/v1/users/{id}/deactivate` | ADMIN | Nonaktifkan pengguna dan cabut sesinya |
| PUT | `/v1/users/{id}/roles` | ADMIN | Ganti seluruh role pengguna |
| PUT | `/v1/users/{id}/password` | Pemilik akun atau ADMIN | Ganti kata sandi sendiri |
| POST | `/v1/users/{id}/reset-password` | ADMIN | Tetapkan kata sandi baru tanpa perlu kata sandi lama |

### Role

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/v1/roles` | Terautentikasi | Daftar semua role |
| POST | `/v1/roles` | ADMIN | Buat role baru |
| DELETE | `/v1/roles/{id}` | ADMIN | Hapus role yang belum dipakai |

### Jejak Audit

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/v1/audit-logs` | ADMIN, AUDITOR | Riwayat perubahan penting |

### Operasional

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/actuator/health` | Publik | Health check layanan |
| GET | `/actuator/metrics` | ADMIN | Metrik runtime |

---

## Dokumentasi OpenAPI

| Halaman | Alamat | Akses |
|---|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui.html> | Publik di profil dev |
| Deskripsi OpenAPI | <http://localhost:8080/v3/api-docs> | Publik (dimatikan di profil produksi) |

Di Swagger UI, klik **Authorize** lalu tempel token akses hasil login untuk mencoba
endpoint yang butuh autentikasi.

---

## Pengujian

### Uji manual

Panduan lengkap ada di [`docs/manual-testing.md`](docs/manual-testing.md).
Berkas [`http/iam-service.http`](http/iam-service.http) berisi 40 permintaan
yang bisa langsung dijalankan dari HTTP Client bawaan IntelliJ.

### Uji otomatis

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd test

# Linux dan macOS
./mvnw test
```

Pengujian integrasi memakai **Testcontainers**: satu container PostgreSQL 16
dibuat sekali per proses pengujian, lalu dimatikan otomatis. Database
pengembangan di port `5433` tidak disentuh, dan berkas `.env` tidak diperlukan
untuk pengujian. Yang dibutuhkan hanya Docker yang sedang berjalan.

| Jenis | Cakupan |
|---|---|
| Unit test | Logika use case, rate limiter, paginasi, aturan kata sandi, audit penolakan |
| Integration test | Matriks otorisasi HTTP lewat MockMvc, dengan database sungguhan di container |
| Concurrency test | Dua PATCH bersamaan atas satu data hanya boleh satu yang berhasil |
| Architecture test | Pemeriksaan arah dependensi antar lapisan |
| Tracing test | Memastikan jembatan OpenTelemetry benar-benar membuat rentang saat sampling dinyalakan |

Laporan cakupan kode dihasilkan oleh JaCoCo:

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd verify

# Linux dan macOS
./mvnw verify

# laporan HTML: target/site/jacoco/index.html
```

Cakupan baris **setiap kelas** minimal 80 persen. Aturan dipasang pada element
`CLASS`, bukan gabungan seluruh paket, supaya satu kelas yang sama sekali tidak
diuji tidak bisa tertutupi kelas lain yang cakupannya tinggi. Satu-satunya
pengecualian adalah `IamServiceApplication`, karena isinya hanya titik masuk.
Build gagal kalau ada kelas yang berada di bawah ambang.

Perintah `verify` juga menjalankan pemeriksa statis dan membuat SBOM:

| Pemeriksa | Aturan | Letak konfigurasi |
|---|---|---|
| Checkstyle | Impor, tab, akhir berkas, panjang baris 120, kurung kurawal wajib, satu pernyataan per baris, urutan modifier | `config/checkstyle/checkstyle.xml` |
| SpotBugs | Temuan berprioritas Medium ke atas, dengan pengecualian sempit yang beralasan | `pom.xml`, `config/spotbugs/exclude.xml` |
| CycloneDX | SBOM CycloneDX 1.6 berisi seluruh komponen yang dipakai | `pom.xml`, keluaran `target/bom.json` |

Di GitHub Actions, setelah image dibangun, Trivy memindai image tersebut.
Temuan HIGH atau CRITICAL yang sudah punya perbaikan menggagalkan build.
Jadwal mingguan tetap menjalankan pemindaian meskipun tidak ada perubahan kode,
supaya kerentanan baru pada dependensi lama tetap ditemukan.

---

## Manajemen Secret

Untuk pengembangan lokal, `.env` di direktori kerja sudah cukup. Untuk
server sungguhan, **secret tidak boleh ada di disk**. Panduan lengkapnya ada
di [`docs/deployment.md`](docs/deployment.md).

Ringkasnya:

| Secret | Environment variable | Sumber yang disarankan |
|---|---|---|
| Kunci token | `JWT_SECRET` | Secret manager atau CI/CD |
| Kata sandi database | `POSTGRES_PASSWORD` | Secret manager atau CI/CD |
| Sisanya | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_HOST`, `POSTGRES_PORT`, `CORS_ALLOWED_ORIGINS` | Environment variable biasa |

### Pengaman commit

Berkas `.env` sudah dikecualikan oleh `.gitignore`. Ada pengaman tambahan
berupa hook yang menolak commit berisi berkas `.env`:

```bash
git config core.hooksPath .githooks
```

Perintah ini cukup dijalankan sekali per mesin. Hook-nya menolak `.env`,
`.env.local`, dan sejenisnya, tetapi tetap mengizinkan `.env.example`.

---

## Teknologi

Semua pustaka yang dipakai, alasan memilihnya, dan tempat penggunaannya
terdokumentasi di [`docs/libraries.md`](docs/libraries.md).

| Kategori | Pustaka |
|---|---|
| Web | Spring MVC (Spring Boot 4.1.2) |
| Data | Spring Data JPA + Hibernate ORM 7 |
| Database | PostgreSQL 16 |
| Migrasi | Flyway 12 |
| Keamanan | Spring Security 7 + Nimbus JOSE JWT |
| Dokumentasi | springdoc-openapi 3.2 |
| Operasional | Spring Boot Actuator |
| Observability | Micrometer Tracing + OpenTelemetry (OTLP/HTTP) |
| Pengujian | Testcontainers PostgreSQL 16 |

---

## Batasan yang Diketahui

Beberapa hal yang biasanya ada di layanan produksi belum tersedia di sini.
Daftar ini sengaja ditulis supaya batasannya jelas sejak awal.

| Kekurangan | Keadaan sekarang | Tindak lanjut |
|---|---|---|
| Cakupan diukur gabungan | Beberapa kelas di bawah 80 persen, misalnya `Role` dan `UserJpaEntity` | Tambahkan tes saat kelas itu berubah |
| Belum ada versi API | Semua endpoint berada di root, tanpa prefiks `/v1` | Tambahkan prefiks versi sebelum ada klien luar |
| Belum ada pendaftaran mandiri | Akun hanya dibuat oleh ADMIN, tanpa verifikasi surel atau reset lewat surel | Tambahkan alur undangan atau verifikasi kalau dibutuhkan |
| Jejak audit bisa diubah | Pemegang akses database dapat mengubah isi `audit_logs` | Kirim salinan ke penyimpanan append-only kalau sudah dibutuhkan |
| Pemindaian CVE tidak memeriksa seluruh pohon dependensi | `dependency-review-action` hanya memeriksa perubahan pada pull request, sedangkan Dependabot alerts mencakup dependensi yang sudah ada | Tinjau peringatan Dependabot secara berkala; jalankan pemindai penuh sebelum rilis besar |

---

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [`docs/onboarding.md`](docs/onboarding.md) | Panduan hari pertama untuk developer baru |
| [`docs/libraries.md`](docs/libraries.md) | Setiap library yang dipakai, kenapa dipilih, dan di mana digunakan |
| [`docs/peta-kode.md`](docs/peta-kode.md) | Peta kode: peran setiap berkas |
| [`docs/glossary.md`](docs/glossary.md) | Kamus istilah yang dipakai di kode |
| [`docs/security.md`](docs/security.md) | Model keamanan, kontrol yang ada, dan batasannya |
| [`docs/runbook.md`](docs/runbook.md) | Apa yang dilakukan saat ada masalah |
| [`docs/deployment.md`](docs/deployment.md) | Panduan deployment dan manajemen secret |
| [`docs/manual-testing.md`](docs/manual-testing.md) | Panduan uji manual lengkap |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Konvensi penamaan, format commit, alur review |
| [`SECURITY.md`](SECURITY.md) | Kebijakan pelaporan kerentanan |
| [`CHANGELOG.md`](CHANGELOG.md) | Riwayat perubahan per versi |
| [`docs/adr/`](docs/adr/) | Catatan keputusan arsitektur |

---

## Struktur Proyek

```
src/main/java/id/osprey/iamservice/
├── domain/          entitas dan port, bebas dependensi framework
│   ├── audit/       jejak audit
│   ├── auth/        token sesi, refresh token, kebijakan login
│   ├── security/    port enkripsi kata sandi
│   ├── shared/      galat domain, paginasi, pengurutan
│   └── user/        pengguna dan role
├── application/     use case
│   ├── audit/       pencatatan jejak audit
│   ├── auth/        login, refresh, logout
│   ├── role/        pengelolaan role
│   ├── security/    aturan kekuatan kata sandi
│   └── user/        pengelolaan pengguna
├── infrastructure/  adapter ke framework dan database
│   ├── persistence/ entitas JPA, pemetaan paginasi, adapter repository
│   └── security/    penerbitan JWT, BCrypt
├── adapter/rest/    controller, DTO, penangkap galat
└── config/          konfigurasi keamanan, rate limit, OpenAPI

src/main/resources/
├── db/migration/       migrasi Flyway (sumber kebenaran skema)
├── db/migration-dev/   data contoh, hanya profil dev
└── application*.yaml

docs/adr/          catatan keputusan arsitektur
docs/libraries.md  daftar library yang dipakai dan alasannya
docs/peta-kode.md  peta peran setiap berkas
docs/glossary.md   kamus istilah yang dipakai di kode
docs/security.md   model keamanan dan batasannya
docs/runbook.md    tindakan saat ada masalah
docs/deployment.md panduan deployment dan manajemen secret
docs/manual-testing.md panduan uji manual lengkap
docs/onboarding.md panduan hari pertama developer baru
http/              koleksi permintaan untuk uji manual
scripts/           skrip cadangan dan pemulihan PostgreSQL
config/checkstyle/ aturan Checkstyle yang dijalankan saat verify
config/spotbugs/   pengecualian SpotBugs yang disetujui beserta alasannya
Dockerfile         image multi-stage, profil prod, pengguna tanpa hak istimewa
.github/           alur kerja otomatis, templat issue, dan templat pull request
```

---

## Catatan Keputusan Arsitektur

Keputusan penting beserta alasannya tercatat di folder [`docs/adr/`](docs/adr/).
Ringkasnya:

| Nomor | Judul |
|---|---|
| [0001](docs/adr/0001-flyway-sebagai-sumber-kebenaran-skema.md) | Flyway sebagai sumber kebenaran skema |
| [0002](docs/adr/0002-penguncian-optimistis-melalui-field-version.md) | Penguncian optimistis melalui field version |
| [0003](docs/adr/0003-token-jwt-dengan-rotasi-token-refresh.md) | Token JWT dengan rotasi token refresh |
| [0004](docs/adr/0004-menyimpan-hash-token-refresh.md) | Menyimpan hash token refresh, bukan token mentah |
| [0005](docs/adr/0005-pembatasan-ukuran-halaman-via-properti.md) | Pembatasan ukuran halaman lewat properti |
| [0006](docs/adr/0006-transaksi-terpisah-untuk-jejak-audit.md) | Transaksi terpisah untuk jejak audit |
| [0007](docs/adr/0007-clean-architecture-dengan-port-dan-adapter.md) | Clean Architecture dengan port dan adapter |
| [0008](docs/adr/0008-lepaskan-kunci-asing-jejak-audit.md) | Lepaskan kunci asing pada tabel jejak audit |
| [0009](docs/adr/0009-perbaikan-komentar-migrasi-terapan.md) | Perbaikan komentar migrasi terapan lewat repair checksum |
| [0010](docs/adr/0010-pemeliharaan-berkala-di-dalam-proses.md) | Pemeliharaan berkala di dalam proses aplikasi |
| [0011](docs/adr/0011-deteksi-pemakaian-ulang-token-refresh.md) | Deteksi pemakaian ulang token refresh |
| [0012](docs/adr/0012-database-pengujian-terisolasi-dengan-testcontainers.md) | Database pengujian terisolasi dengan Testcontainers |
| [0013](docs/adr/0013-observability-penolakan-dan-tracing.md) | Observability penolakan akses dan tracing |

Panduan deployment ada di [`docs/deployment.md`](docs/deployment.md).
