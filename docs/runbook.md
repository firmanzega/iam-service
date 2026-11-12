# Runbook

Dokumen ini berisi apa yang harus dilakukan ketika sesuatu bermasalah. Ini
bukan panduan konfigurasi, tapi panduan tindakan.

---

## 1. Diagnosis Cepat

Langkah pertama sebelum panik: cari tahu gejalanya.

```bash
# Apakah layanan hidup?
curl -s http://localhost:8080/actuator/health

# Log aplikasi
# Lihat terminal tempat aplikasi dijalankan.
# Kalau dijalankan sebagai jar, arahkan keluaran ke berkas:
#   java -jar target/iam-service-0.0.1-SNAPSHOT.jar > app.log 2>&1

# Riwayat migrasi database
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

| Gejala | Kemungkinan | Lihat |
|---|---|---|
| Aplikasi gagal start | Secret kurang atau salah | [Bagian 2](#2-aplikasi-gagal-start) |
| `Failed to configure a DataSource` | PostgreSQL mati | [Bagian 3](#3-database-tidak-bisa-dijangkau) |
| `Migrations have failed validation` | Migrasi tidak sinkron | [Bagian 4](#4-migrasi-ditolak) |
| Halaman kosong putih di Swagger UI | Aset diblokir atau CSP terlalu ketat | [Bagian 5](#5-swagger-ui-kosong-putih) |
| Login selalu gagal | Akun terkunci atau data hilang | [Bagian 6](#6-login-selalu-gagal) |
| Semua orang logout | Kunci token dirotasi | [Bagian 7](#7-semua-pengguna-terlogout) |
| Lambat atau timeout | Koneksi database habis | [Bagian 8](#8-lambat-atau-timeout) |
| Tabel `refresh_tokens` membesar terus | Pembersihan berkala mati | [Bagian 9](#9-pemeliharaan-berkala-tidak-berjalan) |

---

## 2. Aplikasi Gagal Start

### Pesan: `iam.jwt.secret minimal 32 byte, sekarang hanya N byte`

`JWT_SECRET` tidak diisi atau terlalu pendek.

```bash
openssl rand -base64 48
```

Isi ke `.env` atau ke environment variable server, lalu jalankan ulang.

### Pesan: `Could not resolve placeholder 'POSTGRES_DB'`

Variabel database belum lengkap. Bandingkan dengan `.env.example`.

**Ingat:** aplikasi menolak start kalau secret hilang. Itu perilaku yang
diinginkan. Jangan dipaksa jalan dengan nilai bawaan.

---

## 3. Database Tidak Bisa Dijangkau

```bash
# Apakah wadahnya hidup?
docker ps

# Kalau tidak, jalankan
docker compose up -d

# Kalau wadahnya hidup tapi tetap gagal, cek log
docker logs iam-service-db
```

Kalau volume-nya rusak atau skemanya salah, database pengembangan bisa
dibuat ulang dari nol:

```bash
# PERINGATAN: semua data hilang
docker compose down -v
docker compose up -d
```

> ⚠️ **Jangan pernah** menjalankan perintah itu di server yang berisi data
> sungguhan.

---

## 4. Migrasi Ditolak

Pesan: `Validate failed: Migrations have failed validation`

### Penyebab A: Migrasi yang sudah pernah dijalankan diedit

Flyway memeriksa checksum. Kalau berbeda, build gagal.

- Kalau itu belum pernah dipakai di mana pun: **baiki** berkasnya, lalu jalankan
  build bersih. Itu menghilangkan masalahnya.
- Kalau sudah dipakai di server lain: **jangan** diedit. Buat migrasi baru
  yang menjalankan perubahannya.

Ada satu pengecualian sempit, yaitu komentar yang rusak dan tidak mengubah satu
perintah SQL pun. Aturannya ada di
[ADR 0009](adr/0009-perbaikan-komentar-migrasi-terapan.md). Langkahnya:

1. Buktikan dulu bahwa diff hanya menyentuh komentar:
   ```bash
   git diff --word-diff -- src/main/resources/db/migration/
   ```
2. Jalankan repair resmi pada setiap database yang sudah menjalankan migrasi
   tersebut:
   ```bash
   flyway -url=jdbc:postgresql://localhost:5433/db_osprey \
     -user=user_osprey repair
   ```
3. Kalau repair resmi tidak tersedia, checksum baru bisa dihitung sendiri.
   Checksum Flyway adalah CRC32 dari setiap baris tanpa pemisah baris, dihitung
   atas byte UTF-8. Sebelum memakai cara ini, bandingkan hasil hitungan untuk
   beberapa migrasi lain dengan nilai yang sudah ada di `flyway_schema_history`.
   Kalau semuanya sama, perbarui satu baris versi yang berubah.

Jangan pernah memakai langkah 3 untuk migrasi yang mengubah SQL. Perbedaan satu
karakter pada perintah SQL tidak boleh disamarkan lewat repair.

### Penyebab B: Migrasi di database tapi berkasnya hilang

```bash
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "select version, description from flyway_schema_history order by installed_rank;"
```

Bandingkan dengan berkas di `src/main/resources/db/migration/`. Kalau ada versi
di database yang tidak ada di berkas, kemungkinan besar itu hasil penamaan
ulang yang tidak bersih.

### Penyebab C: Urutan versi bentrok

Kalau migrasi data contoh memakai nomor versi besar, migrasi utama yang
ditambahkan belakangan dianggap *out of order*. Pakai nomor versi desimal
untuk migrasi khusus dev, contoh `V6_5__data_contoh.sql`.

---

## 5. Swagger UI Kosong Putih

### Periksa dulu apakah asetnya termuat

```bash
curl -s -o NUL -w '%{http_code}\n' http://localhost:8080/swagger-ui/index.html
curl -s -o NUL -w '%{http_code}\n' http://localhost:8080/webjars/swagger-ui/swagger-ui-bundle.js
```

Kalau 401, path-nya diblokir aturan keamanan. Tambahkan matcher `permitAll`.

Kalau 200 tapi halamannya tetap kosong, periksa header CSP:

```bash
curl -s -D - -o NUL http://localhost:8080/swagger-ui/index.html | findstr /i "content-security-policy"
```

Kalau isinya `default-src 'none'`, semua skrip dan gaya diblokir. Longgarkan
menjadi `default-src 'self'` beserta directive yang lebih terarah.

Buka juga konsol browser untuk pesan CSP yang persis.

---

## 6. Login Selalu Gagal

```bash
# Apakah akunnya ada dan aktif?
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "select id, username, active, failed_login_attempts, locked_until from users where username = 'admin';"
```

Kalau `locked_until` masih di masa depan, akun terkunci. Tunggu sampai
kedaluwarsanya, atau reset manual:

```bash
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "update users set failed_login_attempts = 0, locked_until = null where username = 'admin';"
```

Kalau kata sandinya lupa, tetapkan ulang lewat API sebagai administrator:

```bash
curl -X POST http://localhost:8080/users/97/reset-password \
  -H "Authorization: Bearer TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"PasswordBaru123"}'
```

---

## 7. Semua Pengguna Terlogout

Penyebab paling mungkin: `JWT_SECRET` dirotasi.

Semua token yang sudah terbit tidak berlaku lagi karena tanda tangannya
menggunakan kunci lama. Itu perilaku yang diharapkan, bukan bug.

**Agar rotasi berikutnya tidak mengganggu pengguna**, perlu dukungan dua
kunci sekaligus dengan header `kid`. Fitur itu belum ada.

---

## 8. Lambat atau Timeout

### Connection pool habis

```bash
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "select count(*) as koneksi_aktif from pg_stat_activity where datname = 'db_osprey';"
```

Kalau mendekati batas pool, naikkan `spring.datasource.hikari.maximum-pool-size`
di profil produksi. Nilai bakunya ada di `application-prod.yaml`.

### Query lambat

```sql
-- Pakai indeks yang benar?
explain analyze select ... ;
```

Kalau pakai `LIKE '%something%'`, indeks B-tree tidak bisa dipakai. Indeks
trigram yang sudah dibuat untuk `username` dan `email` seharusnya membantu
pola seperti itu.

### Deadlock

Kalau muncul `deadlock detected`, periksa apakah ada kunci asing yang
menunjuk ke tabel yang sama. Lihat
[ADR 0008](adr/0008-lepaskan-kunci-asing-jejak-audit.md).

---

## 9. Pemeliharaan Berkala Tidak Berjalan

Dua tugas latar berjalan di dalam proses aplikasi, bukan lewat penjadwal
terpisah. Alasannya ada di
[ADR 0010](adr/0010-pemeliharaan-berkala-di-dalam-proses.md).

| Tugas | Selang bawaan | Kunci konfigurasi |
|---|---|---|
| Hapus token refresh yang sudah kedaluwarsa | 1 jam | `iam.refresh-token-cleanup.enabled`, `iam.refresh-token-cleanup.interval` |
| Buang entri rate limiter yang tidak aktif | 10 menit | `iam.rate-limit.cleanup-interval` |

Kalau tugas tidak berjalan, gejalanya jarang langsung terlihat. Yang muncul
adalah tabel yang membesar perlahan, atau memori proses yang naik seiring
jumlah alamat IP yang pernah memanggil layanan.

### 9.1 Token refresh menumpuk

Hitung dulu berapa baris yang seharusnya sudah terhapus:

```bash
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "select count(*) as sudah_kedaluwarsa from refresh_tokens where expires_at < now();"
```

Kalau jumlahnya besar dan terus bertambah, periksa dua hal:

1. **Sakelar menyala.** Pastikan `iam.refresh-token-cleanup.enabled` tidak
   diisi `false`. Kalau properti itu tidak ada, nilainya dianggap menyala.
2. **Penjadwal aktif.** Salah satu penyebab paling umum adalah
   `@EnableScheduling` hilang dari kelas aplikasi. Tanpa anotasi itu, method
   `@Scheduled` tidak pernah dipanggil dan tidak ada galat apa pun.

Saat pembersihan berhasil menghapus baris, log menampilkan:

```
Menghapus N token refresh yang sudah kedaluwarsa
```

Baris itu hanya muncul kalau ada yang dihapus, jadi tidak adanya log bukan
bukti bahwa penjadwal mati. Tunggu satu siklus penuh sebelum menyimpulkan.

Pembersihan manual bisa dijalankan bila tabel sudah terlalu besar dan tidak
mau menunggu siklus berikutnya:

```bash
docker exec iam-service-db psql -U user_osprey -d db_osprey \
  -c "delete from refresh_tokens where expires_at < now();"
```

Perintah itu hanya menyentuh token yang **sudah kedaluwarsa**. Jangan
menambahkan syarat lain, misalnya menghapus semua token yang dicabut, karena
baris itu masih dipakai untuk menelusuri pemakaian ulang token.

### 9.2 Memori rate limiter naik

`RateLimitFilter` menyimpan satu antrean waktu per alamat IP. Entri dibuang
saat pemindaian berkala, dan antrean lama dipangkas ketika klien itu kembali
memanggil. Kalau jumlah entri dicurigai terus naik:

- Pastikan `iam.rate-limit.cleanup-interval` tidak diisi nilai yang sangat
  besar. Nilai bawaan sepuluh menit.
- Periksa apakah ada klien yang membanjiri layanan dari banyak alamat IP.
  Pemindaian hanya membuang entri yang **sudah** di luar jendela waktu; entri
  baru tetap bertambah selama serangan berlangsung.
- Menjalankan ulang proses adalah cara paling cepat mengosongkan peta, tetapi
  itu juga menghapus penghitung yang sedang berjalan. Lakukan hanya bila
  memang perlu.

Kalau kebutuhan pembatasan laju sudah lintas instance, pindahkan batasnya ke
gateway atau reverse proxy. Rate limiter di dalam proses hanya menghitung
per instance.

---

## 10. Deploy dan Rollback

### Deploy

```bash
# Windows (PowerShell)
.\mvnw.cmd clean verify

# Linux dan macOS
./mvnw clean verify
```

```bash
java -jar target/iam-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Pastikan environment variable sudah terpasang lebih dulu, terutama
`SPRING_PROFILES_ACTIVE`, `JWT_SECRET`, dan kredensial database.

### Rollback

Migrasi Flyway **tidak** mendukung rollback otomatis. Untuk kembali ke versi
sebelumnya:

1. Hentikan layanan.
2. Kembalikan kode ke commit sebelumnya.
3. Buat migrasi baru yang membatalkan perubahan skema migrasi yang bermasalah.
   Ubah migrasi lama **tidak boleh**, karena checksum-nya sudah tercatat.
4. Jalankan ulang.

Kalau migrasi merusak data dan tidak ada cara memperbaiki, pulihkan dari
cadangan. Karena itu **cadangan database terjadwal** wajib ada.

---

## 11. Dicurigai kebocoran

1. **Cabut seluruh sesi** dengan merotasi `JWT_SECRET`. Semua pengguna
   dipaksa login ulang.
2. **Cek jejak audit** untuk melihat aktivitas yang mencurigakan:
   ```bash
   curl -H "Authorization: Bearer TOKEN_ADMIN" \
     "http://localhost:8080/audit-logs?size=100"
   ```
   Perhatikan `LOGIN_FAILED` yang banyak, `ACCOUNT_LOCKED`, dan perubahan role
   yang tidak wajar.
3. **Nonaktifkan akun** yang dicurigai:
   ```bash
   curl -X POST http://localhost:8080/users/{id}/deactivate \
     -H "Authorization: Bearer TOKEN_ADMIN"
   ```
4. **Rotasi kredensial database** kalau yang bocor adalah akses database.
5. **Catat kejadiannya** di `CHANGELOG.md` sesuai
   [kebijakan pelaporan](https://github.com/firmanzega/iam-service/blob/main/SECURITY.md).

---

## 12. Kontak

| Keperluan | Ke mana |
|---|---|
| Pelaporan kerentanan | Tab Security repositori, lihat `SECURITY.md` |
| Riwayat perubahan | `CHANGELOG.md` |
| Keputusan arsitektur | `docs/adr/` |
| Cara memakai layanan | `docs/manual-testing.md` |
| Cara deploy | `docs/deployment.md` |
