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
| Java | 21 (LTS) | Sudah terpasang bersama Maven Wrapper |
| Maven | 3.10+ | Dipakai lewat Maven Wrapper (tidak perlu diinstal) |
| PostgreSQL | 16 | Jalankan lewat Docker Compose |
| Docker | - | Hanya untuk menjalankan PostgreSQL |

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

> **Ganti kata sandinya setelah login pertama.** Akun ini hanya ada di profil `dev`
> dan tidak pernah aktif di produksi.

### 4. Cek cepat

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin","password":"Admin1234"}'
```

---

## Konfigurasi

Semua kredensial dan parameter sensitif dibaca dari environment variable. Berkas `.env`
diisi otomatis oleh Spring Boot lewat `spring.config.import`, sehingga **tidak perlu
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

Parameter lain ada di `src/main/resources/application.yaml` pada blok `iam`:

| Properti | Bawaan | Keterangan |
|---|---|---|
| `iam.jwt.access-token-ttl` | `15m` | Masa berlaku token akses |
| `iam.jwt.refresh-token-ttl` | `7d` | Masa berlaku token refresh |
| `iam.login.max-failed-attempts` | `5` | Batas kegagalan login sebelum akun terkunci |
| `iam.login.lock-duration` | `15m` | Lama akun terkunci |
| `iam.rate-limit.max-requests` | `20` | Batas permintaan per menit pada endpoint autentikasi |
| `iam.rate-limit.window` | `1m` | Panjang jendela rate limit |

---

## Daftar Endpoint

Semua endpoint berada di bawah `/`, memakai JSON, dan memakai format galat yang sama:

```json
{
  "status": 409,
  "error": "Conflict",
  "timestamp": "2026-01-01T10:00:00",
  "path": "/users",
  "message": "Username already exists",
  "details": {}
}
```

### Autentikasi

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| POST | `/auth/login` | Publik | Terima token akses dan token refresh |
| POST | `/auth/refresh` | Publik | Tukar token refresh (token lama dicabut) |
| POST | `/auth/logout` | Terautentikasi | Cabut token refresh milik pemanggil |

### Pengguna

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/users` | ADMIN, AUDITOR | Daftar pengguna, mendukung pencarian dan pengurutan |
| GET | `/users/{id}` | ADMIN, AUDITOR, atau pemilik akun | Ambil satu pengguna |
| POST | `/users` | ADMIN | Buat pengguna baru |
| PUT | `/users/{id}` | ADMIN | Ganti seluruh data pengguna |
| PATCH | `/users/{id}` | ADMIN | Ubah sebagian data pengguna |
| POST | `/users/{id}/activate` | ADMIN | Aktifkan kembali pengguna |
| POST | `/users/{id}/deactivate` | ADMIN | Nonaktifkan pengguna dan cabut sesinya |
| PUT | `/users/{id}/roles` | ADMIN | Ganti seluruh role pengguna |
| PUT | `/users/{id}/password` | Pemilik akun atau ADMIN | Ganti kata sandi sendiri |
| POST | `/users/{id}/reset-password` | ADMIN | Tetapkan kata sandi baru tanpa perlu kata sandi lama |

### Role

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/roles` | Terautentikasi | Daftar semua role |
| POST | `/roles` | ADMIN | Buat role baru |
| DELETE | `/roles/{id}` | ADMIN | Hapus role yang belum dipakai |

### Jejak Audit

| Method | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/audit-logs` | ADMIN, AUDITOR | Riwayat perubahan penting |

### Operasional

| Metode | Path | Akses | Keterangan |
|---|---|---|---|
| GET | `/actuator/health` | Publik | Health check layanan |
| GET | `/actuator/metrics` | ADMIN | Metrik runtime |

---

## Dokumentasi OpenAPI

| Halaman | Alamat | Akses |
|---|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui.html> | Publik |
| Deskripsi OpenAPI | <http://localhost:8080/v3/api-docs> | ADMIN |

Di Swagger UI, klik **Authorize** lalu tempel token akses hasil login untuk mencoba
endpoint yang butuh autentikasi.

---

## Pengujian

### Uji manual

Panduan lengkap ada di [`docs/manual-testing.md`](docs/manual-testing.md).
Berkas [`http/iam-service.http`](http/iam-service.http) berisi 40+ permintaan
yang bisa langsung dijalankan dari HTTP Client bawaan IntelliJ.

### Uji otomatis

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd test

# Linux dan macOS
./mvnw test
```

Berkas `.env` dibaca otomatis, jadi tidak perlu menyiapkan environment variable
secara manual.

| Jenis | Cakupan |
|---|---|
| Unit test | Logika use case, rate limiter, paginasi, aturan kata sandi |
| Integration test | Matriks otorisasi HTTP lewat MockMvc |
| Concurrency test | Dua PATCH bersamaan atas satu data hanya boleh satu yang berhasil |
| Architecture test | Pemeriksaan arah dependensi antar lapisan |

Laporan cakupan kode dihasilkan oleh JaCoCo:

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd verify

# Linux dan macOS
./mvnw verify

# laporan HTML: target/site/jacoco/index.html
```

Cakupan baris harus mencapai **80 persen**. Build gagal kalau ambang itu tidak tercapai.

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
├── db/migration-dev/  data contoh, hanya profil dev
└── application*.yaml

docs/adr/          catatan keputusan arsitektur
docs/deployment.md panduan deployment dan manajemen secret
docs/manual-testing.md panduan uji manual lengkap
http/              koleksi permintaan untuk uji manual
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

Panduan deployment ada di [`docs/deployment.md`](docs/deployment.md).
