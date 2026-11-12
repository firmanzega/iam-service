# Peta Kode

Peta ini menunjukkan setiap berkas di proyek ini dan perannya. Pakai ini
ketika mau cari tahu "di mana logika ini berada".

---

## 1. Gambaran Besar

```
src/main/java/id/osprey/iamservice/
├── IamServiceApplication.java      titik masuk
├── domain/                        aturan bisnis murni
├── application/                   orkestrasi use case
├── infrastructure/                adapter ke framework dan database
├── adapter/rest/                  lapisan HTTP
└── config/                        konfigurasi framework
```

Arah dependensi hanya satu: `adapter` -> `application` -> `domain`. Arah
baliknya dilarang dan dijaga oleh `ArchitectureLayerTest`.

---

## 2. Titik Masuk

| Berkas | Peran |
|---|---|
| `IamServiceApplication.java` | Titik masuk Spring Boot. Mengaktifkan pemindaian properti konfigurasi dengan `@ConfigurationPropertiesScan` dan penjadwal tugas latar dengan `@EnableScheduling` |

---

## 3. Lapisan Domain

Aturan bisnis murni. **Tidak ada satu pun import framework di sini.**

### 3.1 `domain/user/`

| Berkas | Peran |
|---|---|
| `User.java` | Pengguna. Aturan bisnisnya: `deactivate()`, `activate()`, `changeUsername()`, `changePassword()`, `registerFailedLogin()`, `clearFailedLogins()`, `isLocked()` |
| `Role.java` | Peran pengguna. Perbandingan berdasarkan nama, bukan identitas objek |
| `UserRepository.java` | **Port** untuk akses data pengguna |
| `RoleRepository.java` | **Port** untuk akses data role |
| `UserSortableProperties.java` | Daftar properti yang boleh jadi kunci pengurutan |

### 3.2 `domain/auth/`

| Berkas | Peran |
|---|---|
| `RefreshToken.java` | Token refresh beserta status pencabutannya |
| `RefreshTokenRepository.java` | **Port** untuk penyimpanan token refresh |
| `TokenIssuer.java` | **Port** untuk penerbitan token dan penghitungan hash |
| `TokenRevocationService.java` | **Port** untuk pencabutan seluruh token milik seorang pengguna |
| `LoginPolicy.java` | Aturan penguncian akun: jumlah kegagalan dan lama penguncian |
| `TokenSettings.java` | Pengaturan penerbitan token: penerbit dan masa berlaku |

### 3.3 `domain/audit/`

| Berkas | Peran |
|---|---|
| `AuditLog.java` | Catatan jejak audit |
| `AuditLogRepository.java` | **Port** untuk penyimpanan jejak audit |
| `AuditLogSortableProperties.java` | Daftar properti yang boleh jadi kunci pengurutan jejak audit |

### 3.4 `domain/shared/`

| Berkas | Peran |
|---|---|
| `DomainException.java` | Galat bisnis beserta alasan dan kategorinya |
| `PaginationRequest.java` | Permintaan halaman milik domain |
| `PageResult.java` | Hasil satu halaman milik domain |
| `SortSpec.java` | Arah pengurutan satu properti |

### 3.5 `domain/security/`

| Berkas | Peran |
|---|---|
| `PasswordEncoder.java` | **Port** untuk hash dan verifikasi kata sandi |

---

## 4. Lapisan Application

Use case. Mengenal domain, tidak mengenal HTTP maupun JPA.

| Berkas | Peran |
|---|---|
| `application/user/UserService.java` | Use case pengguna: cari, lihat, buat, ubah, ganti role, ganti kata sandi sendiri, reset kata sandi, nonaktifkan, aktifkan |
| `application/auth/AuthService.java` | Use case autentikasi: login, tukar refresh token, logout, cabut semua token. Sekaligus implementasi `TokenRevocationService` |
| `application/auth/LoginAttemptService.java` | Mencatat kegagalan login di transaksi terpisah |
| `application/auth/RefreshTokenCleanupService.java` | Menghapus token refresh yang sudah kedaluwarsa. Dipisahkan dari penjadwal supaya aturannya bisa diuji tanpa menyalakan Spring |
| `application/auth/TokenPair.java` | Hasil login berupa pasangan token |
| `application/role/RoleService.java` | Use case role: daftar, buat, hapus |
| `application/audit/AuditService.java` | Pencatatan dan pembacaan jejak audit. `record` ikut transaksi, `recordIndependently` memakai transaksi terpisah, `findAll` membaca halaman jejak audit |
| `application/security/PasswordPolicy.java` | Aturan kekuatan kata sandi |

---

## 5. Lapisan Infrastructure

Adapter yang mengimplementasikan port dari domain.

### 5.1 `infrastructure/persistence/`

| Berkas | Peran |
|---|---|
| `UserJpaEntity.java` | Entitas JPA tabel `users`, termasuk relasi role dan kolom `version` |
| `RoleJpaEntity.java` | Entitas JPA tabel `roles` |
| `RefreshTokenJpaEntity.java` | Entitas JPA tabel `refresh_tokens` |
| `AuditLogJpaEntity.java` | Entitas JPA tabel `audit_logs` |
| `SpringDataUserRepository.java` | Repository Spring Data untuk pengguna |
| `SpringDataRoleRepository.java` | Repository Spring Data untuk role |
| `SpringDataRefreshTokenRepository.java` | Repository Spring Data untuk token refresh |
| `SpringDataAuditLogRepository.java` | Repository Spring Data untuk jejak audit |
| `UserPersistenceAdapter.java` | Mengimplementasikan port `UserRepository` |
| `RolePersistenceAdapter.java` | Mengimplementasikan port `RoleRepository` |
| `RefreshTokenPersistenceAdapter.java` | Mengimplementasikan port `RefreshTokenRepository` |
| `AuditLogPersistenceAdapter.java` | Mengimplementasikan port `AuditLogRepository` |
| `PaginationTranslator.java` | Menerjemahkan tipe paginasi domain ke dan dari Spring Data |

### 5.2 `infrastructure/security/`

| Berkas | Peran |
|---|---|
| `JwtTokenService.java` | Menerbitkan token JWT dan menghitung hash SHA-256 token refresh |
| `BCryptPasswordEncoderAdapter.java` | Mengimplementasikan port `PasswordEncoder` memakai BCrypt |

---

## 6. Lapisan Adapter REST

| Berkas | Peran |
|---|---|
| `adapter/rest/UserController.java` | Endpoint pengguna |
| `adapter/rest/AuthController.java` | Endpoint login, refresh, dan logout |
| `adapter/rest/RoleController.java` | Endpoint role |
| `adapter/rest/AuditController.java` | Endpoint jejak audit |
| `adapter/rest/WebMapper.java` | Penerjemah bersama dari tipe web ke tipe domain: identitas aktor dan permintaan pagination |
| `adapter/rest/GlobalExceptionHandler.java` | Menerjemahkan galat menjadi JSON. Termasuk pemetaan kategori `DomainException` ke status HTTP |
| `adapter/rest/ErrorResponse.java` | Bentuk galat yang seragam |

### 6.1 `adapter/rest/dto/`

| Berkas | Peran |
|---|---|
| `CreateUserRequest.java` | Body untuk membuat pengguna |
| `UpdateUserRequest.java` | Body untuk mengganti seluruh data pengguna |
| `PatchUserRequest.java` | Body untuk mengubah sebagian data pengguna |
| `ChangePasswordRequest.java` | Body untuk mengganti kata sandi sendiri |
| `ResetPasswordRequest.java` | Body untuk menetapkan kata sandi baru oleh administrator |
| `ReplaceRolesRequest.java` | Body untuk mengganti seluruh role pengguna |
| `LoginRequest.java` | Body untuk login |
| `RefreshRequest.java` | Body untuk tukar token refresh |
| `CreateRoleRequest.java` | Body untuk membuat role |
| `UserResponse.java` | Bentuk data pengguna yang dikembalikan |
| `RoleResponse.java` | Bentuk data role yang dikembalikan |
| `TokenResponse.java` | Bentuk pasangan token yang dikembalikan |
| `AuditLogResponse.java` | Bentuk catatan audit yang dikembalikan |
| `PageResponse.java` | Bentuk halaman data yang dikembalikan |

---

## 7. Lapisan Config

| Berkas | Peran |
|---|---|
| `config/SecurityConfig.java` | Aturan keamanan: filter, aturan akses per URL, header keamanan, CORS, serta bean `JwtEncoder` dan `JwtDecoder` |
| `config/IamProperties.java` | Konfigurasi khusus layanan, dibungkus sebagai `@ConfigurationProperties` |
| `config/DomainConfig.java` | Mengubah konfigurasi menjadi objek domain, misalnya `LoginPolicy` dan `TokenSettings` |
| `config/RateLimitFilter.java` | Pembatas laju permintaan pada endpoint autentikasi, sekaligus membuang entri klien yang sudah tidak aktif |
| `config/RefreshTokenCleanupJob.java` | Penjadwal pembersihan token refresh kedaluwarsa. Bisa dimatikan lewat `iam.refresh-token-cleanup.enabled` |
| `config/RestAuthenticationEntryPoint.java` | Menjawab permintaan tanpa kredensial dengan 401 |
| `config/RestAccessDeniedHandler.java` | Menjawab permintaan yang tidak berwenang dengan 403 |
| `config/SecurityErrorWriter.java` | Menulis galat keamanan dalam bentuk JSON |
| `config/OpenApiConfig.java` | Deskripsi OpenAPI dan skema keamanan bearer |
| `config/DevAdminBootstrap.java` | Membuat akun administrator pada profil dev. **Tidak aktif di produksi** |

---

## 8. Pengujian

| Berkas | Peran |
|---|---|
| `ArchitectureLayerTest.java` | Memeriksa arah dependensi antar lapisan |
| `IamServiceApplicationTests.java` | Memastikan konteks aplikasi bisa start |
| `adapter/rest/AuthSecurityIntegrationTest.java` | Matriks otorisasi lewat HTTP, plus alur ganti kata sandi, kunci akun, dan logout |
| `adapter/rest/GlobalExceptionHandlerTest.java` | Pemetaan galat ke status HTTP |
| `application/auth/AuthServiceTest.java` | Unit test alur autentikasi |
| `application/auth/RefreshTokenCleanupServiceTest.java` | Unit test aturan penghapusan token kedaluwarsa |
| `application/auth/RefreshTokenCleanupIntegrationTest.java` | Membuktikan hanya token kedaluwarsa yang terhapus di database |
| `application/audit/AuditServiceTest.java` | Mengunci urutan bawaan dan penolakan properti sort asing |
| `application/user/UserServiceTest.java` | Unit test use case pengguna |
| `application/user/UserConcurrencyTest.java` | Dua PATCH bersamaan atas satu data |
| `application/role/RoleServiceTest.java` | Unit test use case role |
| `application/security/PasswordPolicyTest.java` | Unit test aturan kekuatan kata sandi |
| `config/PaginationConfigTest.java` | Mengunci batas ukuran halaman agar tidak berubah tanpa disadari |
| `config/RateLimitFilterTest.java` | Unit test pembatas laju permintaan, termasuk pemangkasan antrean dan pembuangan entri basi |

---

## 9. Konfigurasi dan Sumber Daya

| Lokasi | Isi |
|---|---|
| `src/main/resources/application.yaml` | Konfigurasi bersama, batas ukuran halaman, konfigurasi OpenAPI dan Actuator |
| `src/main/resources/application-dev.yaml` | Profil pengembangan: SQL dicatat ke log, data contoh, bootstrap administrator |
| `src/main/resources/application-prod.yaml` | Profil produksi: pool koneksi, validasi skema, **springdoc dimatikan** |
| `src/main/resources/db/migration/` | Migrasi skema yang berlaku di semua profil |
| `src/main/resources/db/migration-dev/` | Data contoh, hanya profil dev |
| `http/iam-service.http` | Koleksi permintaan untuk uji manual |
| `.github/workflows/ci.yml` | Alur kerja otomatis: build, uji, gate cakupan, unggah artefak |
| `.github/ISSUE_TEMPLATE/` | Templat issue: laporan bug, permintaan fitur, dan pertanyaan |
| `.github/PULL_REQUEST_TEMPLATE.md` | Templat pull request beserta checklist review |

---

## 10. Di Mana Harus Menaruh Kode Baru

| Kode baru | Tempat |
|---|---|
| Aturan bisnis pada pengguna atau role | `domain/user/` |
| Port untuk akses data baru | `domain/` di paket yang sesuai |
| Use case baru | `application/` di paket yang sesuai |
| Entitas JPA atau adapter repository | `infrastructure/persistence/` |
| Endpoint baru | `adapter/rest/` beserta DTO-nya di `adapter/rest/dto/` |
| Konfigurasi framework | `config/` |

Kalau tidak yakin, baca
[ADR 0007](adr/0007-clean-architecture-dengan-port-dan-adapter.md)
dulu.
