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
| Banyak permintaan 401 atau 403 | Percobaan akses tanpa kredensial atau eskalasi peran | [Bagian 12](#12-penolakan-akses-meningkat) |

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
curl -X POST http://localhost:8080/v1/users/97/reset-password \
  -H "Authorization: Bearer TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"PasswordBaru123"}'
```

---

## 7. Semua Pengguna Terlogout

Penyebab paling sering adalah perubahan kunci token. Bedanya bisa dilihat dari
kapan logout terjadi:

| Penyebab | Ciri | Tindakan |
|---|---|---|
| Kunci diganti tanpa mengisi kunci sebelumnya | Seluruh token lama langsung ditolak setelah deploy | Untuk rotasi normal, isi `JWT_SECRET_PREVIOUS` dengan kunci lama |
| Kunci sebelumnya dikosongkan terlalu cepat | Pengguna terlogout setelah masa transisi berakhir | Tunggu minimal 15 menit untuk token akses, atau 7 hari untuk token refresh |
| Kunci berbeda antar instance | Sebagian permintaan 401, sebagian berhasil | Pastikan semua instance membaca konfigurasi yang sama, lalu restart serentak |
| Rotasi darurat | Perilaku yang disengaja setelah dugaan kebocoran | Lanjutkan penanganan insiden pada bagian 11 |

Token yang ditandatangani kunci lama tidak bisa diverifikasi tanpa kunci itu.
Karena itu rotasi normal memerlukan dua kunci sekaligus, dan prosedurnya ada di
[`deployment.md`](deployment.md) bagian 1.

Untuk memastikan token baru memang memakai kunci aktif, periksa header token:

```bash
printf '%s' "$TOKEN" | cut -d. -f1 | base64 -d 2>/dev/null
```

Header memuat `"kid"` yang berbeda untuk setiap kunci. Nilainya diturunkan dari
isi kunci, jadi kunci yang sama selalu menghasilkan `kid` yang sama.

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

Kalau layanan berada di belakang reverse proxy, periksa juga
[`deployment.md`](deployment.md) bagian 4.1. Tanpa pengaturan alamat klien yang
benar, seluruh trafik terlihat berasal dari satu alamat dan kuota dipakai
bersama.

### 9.3 Peringatan dependensi

Ada dua jalur pemantauan yang saling melengkapi:

| Jalur | Kapan | Yang diperiksa |
|---|---|---|
| `dependency-review-action` | Setiap pull request ke `main` | Dependensi yang ditambahkan atau diperbarui. Build gagal kalau ada kerentanan High |
| Dependabot alerts | Berkelanjutan, dengan pembaruan mingguan | Seluruh dependensi yang sudah ada |

Langkah saat ada peringatan:

1. Buka pull request yang ditandai **dependency review**, atau tab
   **Security → Dependabot alerts**.
2. Periksa apakah versi yang dipakai memang terpengaruh:
   ```bash
   .\mvnw.cmd dependency:tree -Dincludes=groupId:artifactId
   ```
3. Kalau perbaikan tersedia, gabungkan pull request Dependabot, lalu jalankan
   `clean verify`.
4. Kalau belum ada perbaikan, catat alasannya di issue supaya tidak terlupakan.

Perlu diingat: dependency review hanya memeriksa perubahan pada pull request.
Kerentanan pada dependensi lama tidak menggagalkan build, jadi Dependabot
alerts tetap harus ditinjau berkala.

---

## 10. Deploy dan Rollback

### Deploy

```bash
# Windows (PowerShell)
.\mvnw.cmd clean verify

# Linux dan macOS
./mvnw clean verify
```

Jalankan sebagai jar:

```bash
java -jar target/iam-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Atau sebagai image container:

```bash
docker build -t iam-service:2026-11-13 .
docker run -d --name iam-service -p 9090:9090 \
  --env-file /etc/iam-service/env \
  iam-service:2026-11-13
```

Pastikan environment variable sudah terpasang lebih dulu, terutama
`SPRING_PROFILES_ACTIVE`, `JWT_SECRET`, dan kredensial database. Image sudah
memilih profil `prod` secara bawaan.

### Rollback

Migrasi Flyway **tidak** mendukung rollback otomatis. Untuk kembali ke versi
sebelumnya:

1. Hentikan layanan.
2. Kembalikan kode ke commit sebelumnya.
3. Buat migrasi baru yang membatalkan perubahan skema migrasi yang bermasalah.
   Ubah migrasi lama **tidak boleh**, karena checksum-nya sudah tercatat.
4. Jalankan ulang.

Kalau migrasi merusak data dan tidak ada cara memperbaiki, pulihkan dari
cadangan:

```bash
BACKUP_DOCKER_CONTAINER=iam-service-db \
POSTGRES_DB=iam_db POSTGRES_USER=iam_user POSTGRES_PASSWORD=rahasia \
  scripts/restore-postgres.sh cadangan/iam-20261113.dump --yes
```

Pemulihan menimpa objek dengan nama sama. Pastikan nama database tujuan sudah
benar sebelum menambahkan `--yes`. Karena itu **cadangan database terjadwal**
wajib ada.

---

## 11. Dicurigai kebocoran

1. **Putuskan akses seketika.** Ganti `JWT_SECRET` dengan kunci baru dan
   **kosongkan** `JWT_SECRET_PREVIOUS`, lalu restart seluruh instance. Semua
   token akses lama langsung ditolak. Jangan pakai prosedur rotasi normal pada
   bagian ini, karena prosedur itu justru mempertahankan kunci lama.
2. **Cabut seluruh token refresh** di database, supaya sesi tidak bisa
   diperpanjang lagi:
   ```bash
   docker exec iam-service-db psql -U user_osprey -d db_osprey -c \
     "update refresh_tokens set revoked_at = now() where revoked_at is null;"
   ```
   Setelah kunci dirotasi tanpa kunci sebelumnya, token refresh lama sebenarnya
   sudah tidak bisa dipakai karena tanda tangannya tidak lagi dikenali. Langkah
   ini tetap dilakukan supaya keadaan database sesuai dengan keadaan token.
3. **Cek jejak audit** untuk melihat aktivitas yang mencurigakan:
   ```bash
   curl -H "Authorization: Bearer TOKEN_ADMIN" \
     "http://localhost:8080/v1/audit-logs?size=100"
   ```
   Perhatikan `LOGIN_FAILED` yang banyak, `ACCOUNT_LOCKED`, perubahan role
   yang tidak wajar, dan `REFRESH_TOKEN_REUSE`.
   `REFRESH_TOKEN_REUSE` berarti token refresh yang sudah dicabut dipakai
   lagi. Layanan sudah otomatis mencabut seluruh sesi pengguna itu, tetapi
   pemilik akun tetap perlu dihubungi dan diminta mengganti kata sandi.

   Untuk melihat sebaran kejadiannya tanpa membuka satu per satu:

   ```bash
   docker exec iam-service-db psql -U user_osprey -d db_osprey -c \
     "select actor_user_id, count(*) as kejadian, max(created_at) as terakhir
        from audit_logs where action = 'REFRESH_TOKEN_REUSE'
       group by actor_user_id order by terakhir desc;"
   ```

   Periksa juga keutuhan rantai audit. Kalau hasilnya tidak valid, ada baris
   yang diubah atau dihapus di luar alur normal:

   ```bash
   curl -H "Authorization: Bearer TOKEN_ADMIN" \
     "http://localhost:8080/v1/audit-logs/verify"
   ```

4. **Nonaktifkan akun** yang dicurigai:
   ```bash
   curl -X POST http://localhost:8080/v1/users/{id}/deactivate \
     -H "Authorization: Bearer TOKEN_ADMIN"
   ```
5. **Rotasi kredensial database** kalau yang bocor adalah akses database.
6. **Catat kejadiannya** di `CHANGELOG.md` sesuai
   [kebijakan pelaporan](https://github.com/firmanzega/iam-service/blob/main/SECURITY.md).

---

## 12. Rantai jejak audit rusak

Gejalanya: `GET /v1/audit-logs/verify` mengembalikan `valid` bernilai `false`.
Artinya ada baris yang diubah atau dihapus di luar alur normal aplikasi.
Aplikasi sendiri tidak pernah memperbarui atau menghapus baris audit.

1. **Catat kode dan id yang dilaporkan.** Balasan memuat `firstBrokenEntryId`
   dan `reason`. Empat kode yang mungkin muncul:

   | Kode | Arti | Kecurigaan |
   |---|---|---|
   | `HASH_MISMATCH` | Isi baris berubah setelah hash dihitung | Seseorang mengubah isi audit langsung di database |
   | `MISSING_PREDECESSOR` | Baris yang ditunjuk `prev_hash` sudah tidak ada | Ada baris audit yang dihapus |
   | `MULTIPLE_HEADS` | Lebih dari satu awal rantai | Baris ditambahkan tanpa melewati trigger, atau rantai ditulis ulang sebagian |
   | `UNREACHABLE_ENTRY` | Baris tidak bisa dicapai dari awal rantai | Rantai diputus atau dibentuk siklus |

2. **Periksa baris di sekitar id tersebut**, termasuk waktu dan pelakunya:

   ```bash
   docker exec iam-service-db psql -U user_osprey -d db_osprey -c \
     "select id, actor_user_id, action, created_at, prev_hash, entry_hash
        from audit_logs where id between 1 and 20 order by id;"
   ```

3. **Bandingkan dengan cadangan terakhir.** Kalau baris yang dicurigai tidak ada
   di cadangan, kemungkinan besar baris itu dihapus. Kalau isinya berbeda,
   kemungkinan besar isinya diubah.

4. **Jangan memperbaiki dengan mengubah isi baris.** Mengembalikan isi lama
   tidak mengembalikan hash, dan hash yang dihitung ulang akan memutus rantai
   sesudahnya. Pilihan yang benar adalah memulihkan tabel dari cadangan, atau
   menerima bahwa rantai terputus dan mencatatnya sebagai insiden.

5. **Anggap kredensial database sudah bocor.** Rotasi kata sandi database dan
   periksa apakah ada akses langsung di luar aplikasi. Tindak lanjuti seperti
   insiden kebocoran pada bagian 11.

6. **Catat kejadiannya** di `CHANGELOG.md` sesuai
   [kebijakan pelaporan](https://github.com/firmanzega/iam-service/blob/main/SECURITY.md).

Rantai ini mendeteksi perubahan, bukan mencegahnya. Pemegang akses tulis
database masih bisa menghitung ulang seluruh rantai dari awal. Karena itu
salinan berkala ke penyimpanan append-only di luar database tetap diperlukan
untuk jaminan yang lebih kuat.

---

## 13. Penolakan akses meningkat

Langkah pertama adalah membedakan dua jenis penolakan, karena artinya berbeda:

| Metrik | Arti | Tindakan awal |
|---|---|---|
| `reason=unauthorized` (401) | Permintaan tanpa token sah. Bisa token kedaluwarsa, token salah, atau pemindaian | Lihat pola alamat dan waktu. Lonjakan seragam biasanya bukan pengguna sah |
| `reason=access_denied` (403) | Identitas sah, tetapi role-nya tidak cukup | Telusuri pelakunya di jejak audit. Ini sinyal eskalasi peran |

Langkah pemeriksaan:

1. **Lihat metrik** lewat endpoint Actuator dengan token ADMIN:
   ```bash
   curl -H "Authorization: Bearer TOKEN_ADMIN" \
     "http://localhost:8080/actuator/metrics/iam.security.rejected"
   ```
2. **Cari log WARN** dengan kata `Akses ditolak` atau
   `tanpa kredensial sah`. Sejak perubahan terakhir, kedua kejadian dicatat
   pada level WARN, bukan DEBUG.
3. **Telusuri 403 di jejak audit**:
   ```bash
   docker exec iam-service-db psql -U user_osprey -d db_osprey -c \
     "select actor_user_id, details, count(*) as kejadian, max(created_at) as terakhir
        from audit_logs where action = 'ACCESS_DENIED'
       group by actor_user_id, details order by terakhir desc;"
   ```
4. **Kalau satu aktor berulang**, nonaktifkan akunnya, periksa role yang
   diberikan, lalu hubungi pemilik akun. Jangan langsung menghapus jejak
   auditnya.
5. **Kalau 401 melonjak tanpa pola akun**, curigai pemindaian otomatis.
   Periksa apakah layanan hanya bisa dijangkau lewat proxy, dan apakah batas
   permintaan di gateway masih aktif.
6. **Kalau perlu menelusuri satu permintaan utuh**, nyalakan sampling tracing
   sementara, lalu cari `traceId` dari baris log yang mencurigakan:
   ```bash
   export OTEL_TRACES_SAMPLING_PROBABILITY=0.1
   export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://collector:4318/v1/traces
   ```
   Nilai itu dibaca saat aplikasi start, jadi mulai ulang layanan setelah
   mengubahnya. Matikan kembali setelah selesai. Sampling `1.0` hanya untuk
   diagnosis jangka pendek.

Penolakan 401 memang **tidak** disimpan ke jejak audit. Kalau butuh riwayat
dalam jangka panjang, kirim log WARN ke sistem log terpusat.

---

## 14. Kontak

| Keperluan | Ke mana |
|---|---|
| Pelaporan kerentanan | Tab Security repositori, lihat `SECURITY.md` |
| Riwayat perubahan | `CHANGELOG.md` |
| Keputusan arsitektur | `docs/adr/` |
| Cara memakai layanan | `docs/manual-testing.md` |
| Cara deploy | `docs/deployment.md` |
