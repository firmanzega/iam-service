# Panduan Uji Manual

Semua perintah dan keluaran di bawah ini sudah dijalankan sungguhan, jadi
keluarannya bisa dipakai sebagai acuan.

> ## Penting: Perintah Maven Wrapper
>
> Bentuk perintahnya berbeda antara Windows dan Linux/macOS:
>
> | Sistem | Perintah |
> |---|---|
> | **Windows** (PowerShell atau cmd) | `.\mvnw.cmd` |
> | **Linux dan macOS** | `./mvnw` |
>
> Di Windows, `./mvnw` **tidak akan dikenali** dan PowerShell akan
> menampilkan galat "The term './mvnw' is not recognized". Selalu pakai
> `.\mvnw.cmd`, atau `.\mvnw` tanpa ekstensi.
>
> Contoh di bawah memakai bentuk Windows. Di Linux dan macOS, ganti
> `.\mvnw.cmd` menjadi `./mvnw`.

---

## 1. Persiapan

### Jalankan database

```bash
docker compose up -d
```

### Jalankan aplikasi

```bash
.\mvnw.cmd spring-boot:run
```

Aplikasi berjalan di <http://localhost:8080>. Tidak perlu export environment
variable, karena berkas `.env` dibaca otomatis.

### Akun yang tersedia

| Username | Kata sandi | Role | Untuk |
|---|---|---|---|
| `admin` | `Admin1234` | ADMIN | Semua operasi |
| `Dummy1` | `dummy1pass` | ADMIN | Data contoh |
| `Dummy3` | `dummy3pass` | USER | User biasa, tanpa role khusus |
| `Dummy2` | `dummy2pass` | AUDITOR | Audit |

> Akun `admin` dibuat otomatis oleh `DevAdminBootstrap` saat profil `dev`
> aktif dan akun itu belum ada.

---

## 2. Cara paling mudah: HTTP Client bawaan IntelliJ

Berkas [`http/iam-service.http`](../http/iam-service.http) sudah berisi 40
permintaan yang tersusun berurutan.

Cara pakai:

1. Buka `http/iam-service.http` di IntelliJ
2. Klik **▶️** di sebelah kiri setiap permintaan untuk menjalankannya
3. Permintaan yang butuh token memakai variabel `@name`, jadi cukup jalankan
   permintaan login lebih dulu

Variabel yang terisi otomatis:

| Variabel | Terisi dari |
|---|---|
| `loginAdmin.response.body.accessToken` | Permintaan login admin |
| `loginMember.response.body.accessToken` | Permintaan login user biasa |

> Kalau IntelliJ menampilkan "Run with", pilih konfigurasi **HTTP Client**.
> Kalau `.http` tidak dikenali, pasang plugin **HTTP Client** bawaan IntelliJ.

---

## 3. Dengan curl

### 3.1 Login dan ambil token

```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin","password":"Admin1234"}'
```

Keluaran:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5NyIsInJvbGVzIjpbIkFETUlOIl0s...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJpYW0tc2VydmljZSIsInN1YiI6Ijk3...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "roles": ["ADMIN"]
}
```

Simpan token aksesnya. Dari sekarang semua perintah memakai:

```bash
-H "Authorization: Bearer TOKEN_ANDA"
```

> Di PowerShell, simpan token ke variabel supaya tidak perlu menyalin manual:
> ```powershell
> $token = (curl.exe -s -X POST http://localhost:8080/v1/auth/login -H "Content-Type: application/json" --data-raw '{"identifier":"admin","password":"Admin1234"}' | ConvertFrom-Json).accessToken
> ```

### 3.2 Daftar pengguna

```bash
curl -H "Authorization: Bearer TOKEN_ANDA" "http://localhost:8080/v1/users?size=5"
```

```json
{
  "content": [ { "id": 97, "username": "admin", "email": "admin@iam.local", "active": true, "version": 0, "roles": ["ADMIN"] } ],
  "page": { "size": 5, "number": 0, "totalElements": 11, "totalPages": 3 }
}
```

---

## 4. Skenario Uji

### 4.1 Membuat pengguna

```bash
curl -X POST http://localhost:8080/v1/users \
  -H "Authorization: Bearer TOKEN_ANDA" \
  -H "Content-Type: application/json" \
  -d '{"username":"andi","email":"andi@mail.com","password":"Andi12345"}'
```

```json
{"id":1382,"username":"andi","email":"andi@mail.com","active":true,"version":0,"roles":[]}
```

### 4.2 User biasa tidak bisa membaca daftar pengguna

```bash
curl -H "Authorization: Bearer TOKEN_USER_BIASA" "http://localhost:8080/v1/users"
```

```
403 Forbidden
```

Daftar pengguna hanya untuk peran `ADMIN` dan `AUDITOR`.

### 4.3 User biasa hanya boleh melihat akunnya sendiri

```bash
# akun sendiri -> 200
curl -H "Authorization: Bearer TOKEN_USER_BIASA" "http://localhost:8080/v1/users/1382"

# akun orang lain -> 403
curl -H "Authorization: Bearer TOKEN_USER_BIASA" "http://localhost:8080/v1/users/97"
```

```json
{"status":403,"error":"Forbidden","timestamp":"...","path":"/v1/users/97","message":"Access denied"}
```

### 4.4 Memberikan role

```bash
curl -X PUT http://localhost:8080/v1/users/1382/roles \
  -H "Authorization: Bearer TOKEN_ANDA" \
  -H "Content-Type: application/json" \
  -d '{"roleNames":["AUDITOR"],"version":1}'
```

```json
{"id":1382,"username":"andi","email":"andi@mail.com","active":true,"version":2,"roles":["AUDITOR"]}
```

Role di luar daftar akan ditolak:

```bash
-d '{"roleNames":["NGADA"],"version":2}'
```

```json
{"status":400,"error":"Bad Request","message":"Unknown roles: [NGADA]"}
```

### 4.5 Mengganti kata sandi sendiri

```bash
curl -X PUT http://localhost:8080/v1/users/1382/password \
  -H "Authorization: Bearer TOKEN_ANDA" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"Andi12345","newPassword":"Andi54321"}'
```

Keluarannya `204 No Content`. Setelah itu:

| Percobaan | Hasil |
|---|---|
| Login dengan kata sandi lama | `401 Invalid credentials` |
| Login dengan kata sandi baru | `200 OK` |

### 4.6 Membatasi ukuran halaman

```bash
curl -H "Authorization: Bearer TOKEN_ANDA" "http://localhost:8080/v1/users?size=99999"
```

```json
"page": { "size": 50, "number": 0, "totalElements": 11, "totalPages": 1 }
```

Ukuran halaman maksimum 50 baris, berapa pun yang diminta.

### 4.7 Menolak pengurutan pada kolom terlarang

```bash
curl -H "Authorization: Bearer TOKEN_ANDA" "http://localhost:8080/v1/users?sort=passwordHash"
```

```json
{
  "status": 400,
  "message": "Properties not allowed for sorting: [passwordHash]. Allowed: [updatedAt, createdAt, id, username, email, active]"
}
```

### 4.8 Rotasi token refresh

```bash
# tukar token refresh -> 200, dapat pasangan token baru
curl -X POST http://localhost:8080/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"TOKEN_REFRESH_LAMA"}'

# pakai token refresh lama lagi -> 401
```

Token refresh hanya bisa dipakai sekali. Yang berikutnya harus pakai token
hasil tukaran. Memakai ulang token lama bukan hanya ditolak: itu dianggap bukti
pencurian, sehingga seluruh sesi pengguna dicabut. Skenario lengkapnya ada di
[bagian 4.13](#413-pemakaian-ulang-token-refresh-terdeteksi).

### 4.9 Logout

```bash
curl -X POST http://localhost:8080/v1/auth/logout \
  -H "Authorization: Bearer TOKEN_ANDA" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"TOKEN_REFRESH_ANDA"}'
```

`204 No Content`. Setelah itu token refresh tersebut tidak berlaku lagi.

### 4.10 Kunci akun otomatis

```bash
# 5x login gagal
for i in 1 2 3 4 5; do
  curl -X POST http://localhost:8080/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"identifier":"lockme","password":"SalahTotal1"}'
done
```

| Percobaan | Hasil |
|---|---|
| 1 sampai 5 | `401 Invalid credentials` |
| Setelahnya, pakai kata sandi yang **benar** | `423 Locked` |

```json
{"status":423,"error":"Locked","message":"Account is temporarily locked"}
```

Akun terbuka lagi otomatis setelah 15 menit.

### 4.11 Melihat jejak audit

```bash
curl -H "Authorization: Bearer TOKEN_ANDA" "http://localhost:8080/v1/audit-logs?size=5"
```

Tanpa parameter `sort`, hasil diurutkan dari yang terbaru dengan `id` sebagai
pemecah seri. Properti yang boleh dipakai: `id`, `createdAt`, `action`,
`actorUserId`, dan `targetUserId`.

```bash
# Urutkan berdasarkan aksi, menaik
curl -H "Authorization: Bearer TOKEN_ANDA" \
  "http://localhost:8080/v1/audit-logs?sort=action,asc&size=5"

# Properti asing ditolak dengan 400, bukan 500
curl -i -H "Authorization: Bearer TOKEN_ANDA" \
  "http://localhost:8080/v1/audit-logs?sort=details,desc"
```

```
2026-01-12T06:27:58Z | LOGIN_FAILED   | actor=1382 target=1382 | kata sandi salah
2026-01-12T06:27:47Z | USER_CREATED  | actor=97   target=1382 | andi
2026-01-12T06:27:47Z | LOGIN_SUCCESS | actor=1382 target=1382 |
2026-01-12T06:27:36Z | LOGIN_SUCCESS | actor=97   target=97   |
```

Jejak audit hanya untuk peran `ADMIN` dan `AUDITOR`.

Penolakan 403 ikut tercatat di tabel yang sama dengan aksi `ACCESS_DENIED`.
Coba akses daftar pengguna memakai token pengguna biasa, lalu periksa:

```bash
curl -H "Authorization: Bearer TOKEN_ANDA" \
  "http://localhost:8080/v1/audit-logs?sort=createdAt,desc&size=5"
```

Baris terbaru berisi `ACCESS_DENIED` dengan `actorUserId` pelaku dan detail
berupa method serta URI yang ditolak. Penolakan 401 **tidak** menulis baris di
sini; kejadiannya hanya muncul di log WARN dan metrik.

### 4.12 Memeriksa keutuhan rantai audit

Endpoint ini hanya untuk peran `ADMIN`. AUDITOR mendapat 403.

```bash
curl -H "Authorization: Bearer TOKEN_ADMIN" \
  "http://localhost:8080/v1/audit-logs/verify"
```

Balasan untuk rantai yang utuh:

```json
{"valid":true,"checkedEntries":42,"firstBrokenEntryId":null,"reason":null}
```

Untuk membuktikan bahwa pemeriksaan benar-benar bekerja, ubah satu baris
langsung di database, lalu panggil endpoint yang sama:

```sql
update audit_logs set details = 'diubah setelah hash' where id = 1;
```

```json
{"valid":false,"checkedEntries":42,"firstBrokenEntryId":1,"reason":"HASH_MISMATCH"}
```

Menghapus satu baris di tengah rantai menghasilkan kode berbeda, karena baris
sesudahnya kehilangan pendahulu:

```json
{"valid":false,"checkedEntries":41,"firstBrokenEntryId":2,"reason":"MISSING_PREDECESSOR"}
```

Kembalikan keadaan database setelah uji ini. Baris yang sudah diubah tidak bisa
diperbaiki hanya dengan mengembalikan isinya, karena hash ikut berubah; hapus
baris uji tersebut atau pulihkan dari cadangan.

### 4.13 Endpoint operasional

```bash
curl http://localhost:8080/actuator/health
```

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

Metrik penolakan keamanan dibaca dengan token `ADMIN`:

```bash
curl -H "Authorization: Bearer TOKEN_ANDA" \
  "http://localhost:8080/actuator/metrics/iam.security.rejected"
```

Nilai `reason` berisi `unauthorized` untuk 401 dan `access_denied` untuk 403,
ditambah tag `method`. Naikkan angka itu dengan memicu penolakan, lalu panggil
ulang untuk melihat pertambahannya.

Dokumentasi OpenAPI:

| Alamat | Akses |
|---|---|
| <http://localhost:8080/swagger-ui.html> | Publik di profil dev |
| <http://localhost:8080/v3/api-docs> | Publik di profil dev |

Di profil produksi, dokumentasi dimatikan seluruhnya. Kedua `springdoc.api-docs.enabled` dan `springdoc.swagger-ui.enabled` bernilai false, jadi tidak ada halaman HTML yang terekspos ke internet.

### Kalau Swagger UI tampil kosong putih

Dua penyebab yang paling sering:

| Penyebab | Gejala | Perbaikan |
|---|---|---|
| Content Security Policy terlalu ketat | Halaman terbuka tapi **putih kosong**, dan konsol browser penuh pesan "violates the following Content Security Policy" | Longgarkan `contentSecurityPolicy` di `SecurityConfig` |
| Aset statis diblokir Security | Halaman terbuka tapi putih, dan permintaan ke `/webjars/...` atau `/swagger-ui/*.js` menjawab 401 | Tambahkan matcher `permitAll` untuk path tersebut |

Untuk memastikan CSP yang aktif, periksa header respons:

```bash
curl -s -D - -o NUL http://localhost:8080/swagger-ui/index.html | findstr /i "content-security-policy"
```

Swagger UI membutuhkan skrip dan gaya dari origin yang sama, jadi
`default-src 'none'` akan selalu membuat halamannya kosong. Yang tetap
diblokir adalah sumber dari luar.

### 4.14 Pemakaian ulang token refresh terdeteksi

```bash
# 1. Login, simpan refresh token pertama
curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"andi","password":"Rahasia123"}'

# 2. Tukar token pertama, simpan token kedua
curl -s -X POST http://localhost:8080/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"TOKEN_PERTAMA"}'

# 3. Pakai token pertama lagi -> 401
curl -i -X POST http://localhost:8080/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"TOKEN_PERTAMA"}'

# 4. Token kedua ikut mati -> 401
curl -i -X POST http://localhost:8080/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"TOKEN_KEDUA"}'
```

| Langkah | Hasil |
|---|---|
| Tukar token pertama | `200`, dapat pasangan token baru |
| Pakai token pertama lagi | `401 Invalid refresh token` |
| Pakai token kedua | `401 Invalid refresh token`, karena seluruh sesi sudah dicabut |

Langkah 3 dianggap bukti pencurian. Seluruh token refresh pengguna dicabut,
termasuk token kedua. Kejadiannya tercatat di jejak audit:

```bash
curl -H "Authorization: Bearer TOKEN_ADMIN" \
  "http://localhost:8080/v1/audit-logs?size=5"
```

Cari baris dengan aksi `REFRESH_TOKEN_REUSE`. Baris token yang dicabut tetap
tersimpan sampai kedaluwarsa, lalu dibersihkan oleh tugas berkala.

### 4.15 Rotasi kunci token

Uji ini memerlukan dua kali restart aplikasi. Kunci harus panjangnya minimal 32
byte, sama seperti `JWT_SECRET` biasa.

```bash
# 1. Jalankan aplikasi dengan JWT_SECRET=kunci-A, lalu login dan simpan token
curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin","password":"Admin1234"}'
```

2. Hentikan aplikasi, lalu jalankan dengan kunci baru dan kunci lama:

```bash
# Linux dan macOS
JWT_SECRET="kunci-B-yang-panjangnya-minimal-32-byte" \
JWT_SECRET_PREVIOUS="kunci-A-yang-panjangnya-minimal-32-byte" \
./mvnw spring-boot:run

# Windows (PowerShell)
$env:JWT_SECRET = "kunci-B-yang-panjangnya-minimal-32-byte"
$env:JWT_SECRET_PREVIOUS = "kunci-A-yang-panjangnya-minimal-32-byte"
.\mvnw.cmd spring-boot:run
```

3. Panggil endpoint dengan token dari langkah 1. Hasilnya tetap `200`:

```bash
curl -i -H "Authorization: Bearer TOKEN_DARI_KUNCI_A" \
  http://localhost:8080/v1/users
```

4. Hentikan aplikasi, hapus kunci sebelumnya, lalu jalankan lagi:

```powershell
# Windows (PowerShell)
Remove-Item Env:JWT_SECRET_PREVIOUS
.\mvnw.cmd spring-boot:run
```

5. Token yang sama sekarang ditolak dengan `401`.

| Langkah | Hasil |
|---|---|
| Token kunci A dipakai saat kunci A masih menjadi kunci sebelumnya | `200`, token lama masih diterima |
| `JWT_SECRET_PREVIOUS` dikosongkan | `401`, token kunci A tidak lagi diterima |
| Login setelah rotasi | Token baru membawa `kid` kunci B |

Untuk memeriksa `kid` pada token, lihat bagian header:

```bash
printf '%s' "$TOKEN" | cut -d. -f1 | base64 -d 2>/dev/null
```

Nilai `kid` diturunkan dari isi kunci. Kunci yang sama selalu menghasilkan nilai
yang sama, dan kunci berbeda menghasilkan nilai berbeda.

---

## 5. Ringkasan Status Kode

| Kode | Arti | Contoh |
|---|---|---|
| `200` | Berhasil | Login, daftar pengguna |
| `201` | Data dibuat | Buat pengguna, buat role |
| `204` | Berhasil tanpa body | Ganti kata sandi, logout, aktifkan pengguna |
| `400` | Permintaan tidak valid | Role tidak dikenal, sort terlarang, body rusak |
| `401` | Belum terautentikasi | Token hilang, token salah, kata sandi salah |
| `403` | Tidak berwenang | User biasa akses daftar pengguna |
| `404` | Data tidak ada | Pengguna dengan id yang tidak ada |
| `409` | Konflik | Username sudah dipakai, versi data basi |
| `423` | Terkunci | Akun terkunci setelah 5x gagal login |
| `429` | Terlalu banyak permintaan | Rate limit endpoint autentikasi |
| `500` | Galat server | Bug, akan tercatat di log |

---

## 6. Kalau Ada Masalah

### Aplikasi tidak mau start

| Gejala | Penyebab dan solusi |
|---|---|
| `Failed to configure a DataSource` | PostgreSQL belum jalan. Jalankan `docker compose up -d` |
| `iam.jwt.secret minimal 32 byte` | `JWT_SECRET` di `.env` kosong atau terlalu pendek. Isi dengan `openssl rand -base64 48` |
| `Could not resolve placeholder` | Variabel di `.env` belum lengkap. Bandingkan dengan `.env.example` |
| `Validate failed: Migrations have failed validation` | Migrasi di database tidak sama dengan berkas di `src/main/resources/db/migration`. Untuk database pengembangan yang sudah lama, jalankan `.\mvnw.cmd flyway:repair` atau hapus volume Docker dan mulai ulang |

### Login selalu gagal

- Pastikan akun `admin` ada. Cek dengan:
  ```bash
  docker exec iam-service-db psql -U user_osprey -d db_osprey -c "select username, active from users;"
  ```
- Kalau `admin` tidak ada, hapus database dan mulai ulang. Akun itu dibuat
  otomatis saat aplikasi pertama kali jalan.

### Data lama tidak sinkron dengan kode

```bash
docker compose down -v
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Perintah `down -v` menghapus volume database, jadi seluruh data hilang dan
dibuat ulang dari migrasi. **Jangan jalankan di server yang sudah berisi data
asli.**

### Melihat log

Log aplikasi muncul di terminal tempat `.\mvnw.cmd spring-boot:run` dijalankan.
Log SQL-nya sudah aktif di profil `dev`, lengkap dengan nilai parameternya.
