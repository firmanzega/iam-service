# Contributing ke iam-service

Dokumen ini berisi konvensi yang dipakai di proyek ini. Ikuti supaya
perubahan dari siapa pun mudah dibaca orang lain.

---

## 1. Konvensi Penamaan

### 1.1 Nama File

| Jenis | Aturan | Contoh |
|---|---|---|
| Kelas | `PascalCase` | `UserController.java` |
| Record | `PascalCase` | `CreateUserRequest.java` |
| Antarmuka | `PascalCase` | `UserRepository.java` |
| Enum | `PascalCase` | `DomainException.Reason` |
| Berkas konfigurasi | `kebab-case` | `application-dev.yaml` |
| Dokumen | `kebab-case` | `manual-testing.md` |
| Skrip SQL | `UPPER_SNAKE` dengan prefiks versi | `V5__add_search_indexes.sql` |
| Package | huruf kecil tanpa garis | `adapter.rest.dto` |

### 1.2 Nama Metode dan Field

| Jenis | Aturan | Contoh |
|---|---|---|
| Metode | `camelCase`, kata kerja di depan | `findByUsernameOrEmail` |
| Field | `camelCase` | `failedLoginAttempts` |
| Konstanta | `UPPER_SNAKE_CASE` | `MAX_PAGE_SIZE` |
| Paket (layer) | huruf kecil tunggal | `domain`, `application` |

### 1.3 Istilah dalam Kode

Semua kode memakai Bahasa Indonesia, termasuk nama kelas, metode, dan
komentar. Istilah teknis yang lazim dalam Java seperti `Controller`,
`Repository`, `Service`, `Entity`, `Adapter`, dan `Domain` tetap dipakai apa
adanya, karena lebih jelas daripada terjemahannya.

| Dipakai | Hindari |
|---|---|
| `gantiKataSandi` | `updatePassword` |
| `daftarPengguna` | `getUsers` |
| `periksaVersi` | `checkVersion` |
| `findByUsernameOrEmail` | `cariByNamaPenggunaAtauSurel` |

Pengecualian: nama yang sudah menjadi bagian dari antarmuka pustaka, seperti
`findById`, `save`, dan `delete`, dibiarkan apa adanya.

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
| Bahasa | `node tools/check-bahasa.mjs <berkas>` |
| Pengujian | `.\mvnw.cmd test` (Windows) atau `./mvnw test` (Linux dan macOS) |
| Cakupan | `.\mvnw.cmd verify` atau `./mvnw verify` |
| Aturan arsitektur | Bagian dari `.\mvnw.cmd verify` lewat `ArchitectureLayerTest` |

### Pemeriksaan Bahasa

Dokumentasi ditulis dalam Bahasa Indonesia, dan pemeriksaan otomatis menjaga
kualitasnya. Pemeriksa di `tools/check-bahasa.mjs` menangkap:

1. Karakter dari aksara lain yang menyisip tanpa sengaja
2. Kata dan frasa yang sudah pernah salah ditulis
3. Kata fungsi bahasa Inggris yang tercampur di prosa Indonesia
4. Kata yang tertulis dua kali berturut-turut

Kalau pemeriksa menemukan kata yang salah, perbaiki kata itu **dan** tambahkan
ke `tools/kata-salah.txt`. Berkas itu adalah memori kesalahan, supaya kata
yang sama tidak terulang lagi.

Kata yang sebenarnya sah tetapi bukan istilah teknis, misal nama resmi
layanan, bisa ditambahkan ke `tools/frasa-izin.txt`.

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
Yang "apa" sudah terbaca dari diffnya.

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
4. Setel `spring.jpa.hibernate.ddl-auto: validate` sudah aktif, supaya
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

---

## 9. Menambah Library Baru

1. Pakai starter Spring Boot kalau ada. Itu cara paling aman karena versinya
   sudah dikelola `spring-boot-starter-parent`.
2. Pastikan benar-benar dipakai. Kalau cuma satu kelas yang memakainya,
   pustaka penuh mungkin berlebihan.
3. Kalau pustaka itu menyentuh lapisan domain, bungkus di balik port.
4. Perbarui `docs/libraries.md`.
5. Jalankan `.\mvnw.cmd clean verify` (Windows) atau `./mvnw clean verify`.

---

## 10. gaya Komunikasi

Semua issue, pull request, dan komentar pakai Bahasa Indonesia yang baku.
Jangan pakai istilah yang belum jelas artinya bagi pembaca baru.

Untuk perubahan besar, usulkan pendekatan lebih dulu di issue atau
diskusi sebelum menulis kodenya. Perubahan yang besar tanpa kesepakatan lebih
berisiko untuk mengulang pekerjaan.
