# Contributing ke iam-service

Dokumen ini berisi konvensi yang dipakai di proyek ini. Ikuti supaya
perubahan dari siapa pun mudah dibaca orang lain.

---

## 1. Konvensi Penamaan

### 1.1 Nama File

| Jenis | Aturan | Contoh |
|---|---|---|
| Class | `PascalCase` | `UserController.java` |
| Record | `PascalCase` | `CreateUserRequest.java` |
| Interface | `PascalCase` | `UserRepository.java` |
| Enum | `PascalCase` | `DomainException.Reason` |
| Configuration file | `kebab-case` | `application-dev.yaml` |
| Document | `kebab-case` | `manual-testing.md` |
| SQL script | `UPPER_SNAKE` dengan prefiks versi | `V5__add_search_indexes.sql` |
| Package | huruf kecil tanpa garis | `adapter.rest.dto` |

### 1.2 Nama Metode dan Field

| Jenis | Aturan | Contoh |
|---|---|---|
| Method | `camelCase`, kata kerja di depan | `findByUsernameOrEmail` |
| Field | `camelCase` | `failedLoginAttempts` |
| Constant | `UPPER_SNAKE_CASE` | `MAX_PAGE_SIZE` |
| Package (layer) | huruf kecil tunggal | `domain`, `application` |

### 1.3 Istilah dalam Kode

Nama class, method, field, dan constant mengikuti konvensi penamaan Java dalam
bahasa Inggris. Itu letak kebenarannya, dan itu yang dipakai seluruh pustaka
di ekosistem ini. Yang memakai Bahasa Indonesia adalah komentar, nama berkas,
dan dokumentasi.

Istilah teknis yang lazim dalam Java seperti `Controller`, `Repository`,
`Service`, `Entity`, `Adapter`, dan `Domain` dipakai apa adanya, karena lebih
jelas daripada terjemahannya.

| Dipakai | Hindari |
|---|---|
| `changePassword` | `gantiKataSandi` |
| `search` | `daftarPengguna` |
| `checkVersion` | `periksaVersi` |
| `findByUsernameOrEmail` | `cariByNamaPenggunaAtauSurel` |

Pengecualian: nama yang sudah menjadi bagian dari antarmuka pustaka, seperti
`findById`, `save`, dan `delete`, dibiarkan apa adanya.

Nama artifact kode sendiri juga ditulis dalam bahasa Inggris, bahkan ketika
disebut di dalam tabel dokumentasi. Jadi tulis `Class`, `Interface`, `Record`,
`Enum`, `Method`, `Field`, `Constant`, dan `Package`, bukan terjemahannya.
Yang boleh diterjemahkan adalah penjelasan aturannya, bukan nama bendanya.

---

## 2. Struktur Lapisan

Aturan arah dependensi: **satu arah saja**.

```
domain  ->  tidak mengimpor framework apa pun
application  ->  hanya mengenal domain
infrastructure  ->  mengimplementasikan port dari domain
adapter/rest  ->  memanggil use case
```

Aturan ini diperiksa otomatis oleh `ArchitectureLayerTest`. Kalau pengujian itu
gagal, berarti ada lapisan yang bocor.

Penjelasan lengkapnya ada di
[ADR 0007](docs/adr/0007-clean-architecture-dengan-port-dan-adapter.md).

---

## 3. Menulis Kode

### 3.1 Aturan Wajib

1. **Tidak ada setter pada entitas domain.** Perubahan selalu lewat method
   bermakna, misalnya `changeUsername()` atau `deactivate()`. Tujuannya supaya
   aturan bisnis terkunci di satu tempat.
2. **Domain tidak boleh melempar galat HTTP.** Pakai `DomainException` beserta
   alasannya. Pemetaan ke status HTTP ada di
   `adapter/rest/GlobalExceptionHandler`.
3. **Validasi bentuk permintaan di DTO**, yaitu di `adapter/rest/dto`. Aturan
   kekuatan kata sandi berbeda, karena itu aturan bisnis dan dipakai juga
   oleh reset kata sandi oleh administrator.
4. **Use case tidak boleh melempar exception dari framework** ke lapisan atas.
   Exception persistence diterjemahkan menjadi `DomainException` di adapter.
5. **Setiap perubahan perilaku wajib disertai pengujian.** Tidak ada perubahan
   tanpa test.

### 3.2 Gaya

- Empat spasi untuk indentasi, tanpa tab.
- Batas panjang baris sekitar 120 karakter.
- Komentar menjelaskan **kenapa**, bukan **apa**. Kode yang nama sudah jelas
  tidak perlu diberi komentar.
- Bahasa Indonesia, dengan Istilah teknis boleh memakai bahasa Inggris.

---

## 4. Format Kode

Project punya berkas `.editorconfig` yang berlaku untuk semua editor.

| Pemeriksa | Cara menjalankan |
|---|---|
| Format | Editor akan menerapkan otomatis dari `.editorconfig` |
| Pengujian | `.\mvnw.cmd test` (Windows) atau `./mvnw test` (Linux dan macOS) |
| Cakupan | `.\mvnw.cmd verify` atau `./mvnw verify` |
| Aturan arsitektur | Bagian dari `.\mvnw.cmd verify` lewat `ArchitectureLayerTest` |

---

## 5. Format Commit

Mengikuti pola **conventional commit**:

```
<tipe>(<cakupan>): <deskripsi singkat>
```

### 5.1 Tipe yang Dipakai

| Tipe | Kapan dipakai |
|---|---|
| `feat` | Fitur baru |
| `fix` | Perbaikan bug |
| `docs` | Perubahan dokumentasi saja |
| `refactor` | Perubahan struktur tanpa mengubah perilaku |
| `test` | Menambah atau memperbaiki pengujian |
| `chore` | Perawat: konfigurasi, dependensi, perkakas |
| `perf` | Perbaikan kinerja |
| `build` | Perubahan sistem build |
| `ci` | Perubahan alur kerja otomatis |

### 5.2 Cakupan

Cakupan diisi dengan nama area yang relevan, huruf kecil: `auth`, `config`,
`user`, `role`, `audit`, `db`, `docs`, `security`.

### 5.3 Isi Pesan

Baris pertama ringkas dan memakai kata kerja sekarang: "tambah", "perbaiki",
"pindahkan", "hapus".

Baris di bawahnya boleh berisi penjelasan **kenapa** perubahan itu perlu.
Yang "apa" sudah terbaca dari diff-nya.

Contoh:

```
fix(auth): batalkan token refresh yang dipakai ulang

Token refresh sekarang dicabut begitu ditukar. Sebelumnya token lama masih
bisa dipakai, sehingga token yang dicuri tidak langsung jadi tidak
berguna.
```

---

## 6. Hook Commit

Berkas `.env` sudah dikecualikan `.gitignore`. Hook tambahan ini
ditambahkan supaya `.env` tidak bisa masuk commit.

Pasang sekali per mesin:

```bash
git config core.hooksPath .githooks
```

Hook menolak `.env`, `.env.local`, dan sejenisnya, tapi tetap mengizinkan
`.env.example`.

---

## 7. Alur Menambah Migrasi Database

1. Buat berkas baru di `src/main/resources/db/migration/` dengan nomor
   versi berikutnya, contoh `V7__nama_perubahan.sql`.
2. **Jangan pernah** mengubah migrasi yang sudah pernah dijalankan. Flyway
   memeriksa checksum dan build akan gagal kalau isinya berubah.
3. Kalau migrasi hanya untuk data contoh, taruh di `db/migration-dev/` dengan
   nomor versi desimal, contoh `V6_5__data_contoh.sql`. Lokasi itu hanya
   dipindai pada profil dev.
4. Pastikan `spring.jpa.hibernate.ddl-auto: validate` sudah aktif, supaya
   perbedaan skema langsung ketahuan saat boot.

---

## 8. Alur Review

Sebelum membuka pull request:

- [ ] `.\mvnw.cmd clean verify` hijau (Windows) atau `./mvnw clean verify`
- [ ] Cakupan baris tetap di atas 80 persen
- [ ] `ArchitectureLayerTest` hijau
- [ ] Pengujian baru ada untuk perubahan baru
- [ ] Dokumentasi diperbarui: README, `docs/libraries.md` kalau ada library
      baru, dan ADR baru kalau ada keputusan arsitektur baru
- [ ] Tidak ada kredensial yang masuk ke commit
- [ ] Pesan commit mengikuti pola conventional commit

Checklist di atas otomatis jadi templat pull request di
`.github/PULL_REQUEST_TEMPLATE.md`, jadi tidak perlu diketik ulang.

Untuk isu, pilih templat yang sesuai di `.github/ISSUE_TEMPLATE/`. Laporkan
celah keamanan lewat tab Security, bukan lewat issue publik. Lihat
[`SECURITY.md`](SECURITY.md).

Kalau baru pertama kali bergabung, mulai dari
[`docs/onboarding.md`](docs/onboarding.md).

---

## 9. Menambah Library Baru

1. Pakai starter Spring Boot kalau ada. Itu cara paling aman karena versinya
   sudah dikelola `spring-boot-starter-parent`.
2. Pastikan benar-benar dipakai. Kalau hanya satu kelas yang memakainya,
   pustaka penuh mungkin berlebihan.
3. Kalau pustaka itu menyentuh lapisan domain, bungkus di balik port.
4. Perbarui `docs/libraries.md`.
5. Jalankan `.\mvnw.cmd clean verify` (Windows) atau `./mvnw clean verify`.

---

## 10. Gaya Komunikasi

Semua issue, pull request, dan komentar pakai Bahasa Indonesia yang baku.
Jangan pakai istilah yang belum jelas artinya bagi pembaca baru.

Istilah teknis berbahasa Inggris boleh dipakai, tapi pakai yang sama terus-menerus
supaya tidak membingungkan. Pilih satu sebutan untuk satu hal, lalu pakai
sebutan itu di seluruh dokumen. Istilah yang dipakai di kode sudah punya
artinya di [`docs/glossary.md`](docs/glossary.md).

Untuk perubahan besar, usulkan pendekatan lebih dulu di issue atau
diskusi sebelum menulis kodenya. Perubahan yang besar tanpa kesepakatan lebih
berisiko untuk mengulang pekerjaan.
