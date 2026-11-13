# Onboarding

Panduan hari pertama untuk developer yang baru bergabung. Kalau kamu hanya butuh
sepintas, bagian [1 sampai 4](#1-siapkan-alat) cukup untuk menjalankan
layanan.

Targetnya: dari nol sampai aplikasi jalan dan pengujian hijau, sekitar
15 menit.

---

## 1. Siapkan Alat

| Alat | Versi | Cara mendapatkannya |
|---|---|---|
| JDK | 21 | [Adoptium](https://adoptium.net/), pilih versi 21 LTS |
| Maven | Tidak perlu dipasang | Sudah ada di Maven Wrapper, jadi pakai `mvnw` |
| Docker | Versi berapa pun yang aktif | Menjalankan PostgreSQL lokal, Testcontainers untuk pengujian, dan build image |
| Git | Versi berapa pun | Untuk mengambil dan mengirim kode |

Cek semuanya sudah siap:

```bash
java -version
docker --version
git --version
```

Kalau `java -version` menunjukkan versi yang salah, berarti ada JDK lain yang
dipakai lebih dulu. Atur `JAVA_HOME` ke JDK 21.

---

## 2. Ambil Kode

```bash
git clone https://github.com/firmanzega/iam-service.git
cd iam-service
```

---

## 3. Siapkan Konfigurasi

Aplikasi membaca semua kredensial dari environment variable, dan file `.env`
dibaca otomatis. Salin contohnya:

```bash
# Linux dan macOS
cp .env.example .env

# Windows (PowerShell)
Copy-Item .env.example .env
```

Isi `JWT_SECRET` dengan kunci acak sepanjang minimal 32 byte:

```bash
openssl rand -base64 48
```

Aplikasi menolak start kalau `JWT_SECRET` kosong atau kurang dari 32 byte.
Itu perilaku yang diinginkan, bukan error yang perlu dilewati.

`JWT_SECRET_PREVIOUS` boleh dikosongkan pada pengembangan sehari-hari. Variabel
itu hanya diisi saat menguji rotasi kunci, seperti dijelaskan di
[`manual-testing.md`](manual-testing.md) bagian 4.15.

> Jangan pernah commit berkas `.env`. Hook commit sudah disiapkan untuk
> menolaknya. Lihat [CONTRIBUTING bagian 6](../CONTRIBUTING.md#6-hook-commit).

---

## 4. Jalankan Database

```bash
docker compose up -d
```

PostgreSQL berjalan di port `5433`. Skema dan data awal dibuat otomatis
saat aplikasi pertama kali dijalankan, jadi belum ada yang perlu disiapkan
manual.

Cek database-nya hidup:

```bash
docker ps
```

Kalau mau database yang benar-benar bersih dari awal, `docker compose down -v`
lalu `docker compose up -d`. Perintah itu menghapus seluruh data, jadi jangan
dijalankan di server sungguhan.

Database ini hanya dipakai saat aplikasi dijalankan. Pengujian integrasi
memakai container sendiri lewat Testcontainers, jadi data pengembangan tidak
pernah tersentuh oleh tes.

---

## 5. Jalankan Aplikasi

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd spring-boot:run

# Linux dan macOS
./mvnw spring-boot:run
```

Di Windows, `./mvnw` tidak dikenali PowerShell. Selalu pakai `.\mvnw.cmd`.

Aplikasi tersedia di <http://localhost:8080>. Pada profil `dev`, satu akun
administrator dibuat otomatis:

| Field | Nilai |
|---|---|
| Username | `admin` |
| Email | `admin@iam.local` |
| Kata sandi | `Admin1234` |

> Ganti kata sandinya setelah login pertama. Akun ini hanya ada di profil
> `dev` dan tidak pernah aktif di produksi.

---

## 6. Cek Cepat

```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin","password":"Admin1234"}'
```

Kalau jawabannya `200` dan berisi `accessToken`, semuanya beres. Kalau gagal,
[`docs/runbook.md`](runbook.md) punya diagnosis untuk pesan galat yang paling
umum.

---

## 7. Jalankan Pengujian

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd test

# Linux dan macOS
./mvnw test
```

Pengujian integrasi memakai **Testcontainers**. Satu container PostgreSQL 16
dibuat sekali per proses pengujian dan dimatikan otomatis setelah selesai.
Database pengembangan di port `5433` tidak dipakai, dan berkas `.env` tidak
diperlukan untuk pengujian. Syaratnya hanya Docker yang sedang berjalan.

Unduhan image pertama kali bisa memakan waktu satu sampai dua menit. Setelah
image tersimpan lokal, pengujian berikutnya jauh lebih cepat.

Untuk menjalankan seluruh pemeriksaan yang dipakai CI:

```bash
# Windows (PowerShell atau cmd)
.\mvnw.cmd clean verify

# Linux dan macOS
./mvnw clean verify
```

Perintah itu menjalankan pengujian, membuat laporan JaCoCo, memeriksa cakupan
baris **setiap kelas** minimal 80 persen, menjalankan **Checkstyle** dan
**SpotBugs**, serta membuat SBOM CycloneDX. Temuan Checkstyle, SpotBugs, dan
cakupan yang kurang menggagalkan build, sama seperti pengujian yang gagal.

| Pemeriksa | Letak konfigurasi |
|---|---|
| Checkstyle | `config/checkstyle/checkstyle.xml` |
| SpotBugs | `pom.xml` dan `config/spotbugs/exclude.xml` |
| Cakupan | Konfigurasi JaCoCo di `pom.xml` |
| SBOM | Konfigurasi CycloneDX di `pom.xml`, keluaran `target/bom.json` |

Laporan HTML cakupan ada di `target/site/jacoco/index.html`.

Untuk memastikan image container masih bisa dibangun:

```bash
docker build -t iam-service:local .
```

---

## 8. Kenali Proyeknya

Repo ini memakai **Clean Architecture**: arah dependensi hanya satu arah, dari
lapisan luar ke lapisan dalam.

```
adapter/rest  ->  application  ->  domain
```

`domain` tidak boleh mengimpor framework apa pun. Aturan itu dijaga
otomatis oleh `ArchitectureLayerTest`, jadi pengujian gagal kalau ada lapisan
yang bocor.

Untuk tahu peran setiap berkas, baca
[`docs/peta-kode.md`](peta-kode.md). Untuk arti istilah yang dipakai di kode,
baca [`docs/glossary.md`](glossary.md).

---

## 9. Alur Kerja Sehari-hari

### Menambah Endpoint Baru

Urutannya penting, supaya tidak ada aturan bisnis yang bocor ke lapisan HTTP.

1. Bentuk validasi di DTO, di `adapter/rest/dto`. Aturan yang soal bentuk
   permintaan letakkan di sini, bukan di use case.
2. Tambahkan method di use case, di `application/`. Aturan bisnis belum
   boleh tahu soal HTTP.
3. Kalau butuh data baru, tambahkan port di `domain/` lalu adapter-nya di
   `infrastructure/persistence/`.
4. Tambahkan controller di `adapter/rest/`, lengkap dengan anotasi OpenAPI
   supaya kontrak di Swagger ikut sinkron.
5. Tambahkan pengujian di `src/test/java/` dengan struktur paket yang sama.
6. Perbarui dokumentasi: README bagian Daftar Endpoint, dan `docs/peta-kode.md`
   kalau ada berkas baru.

### Menambah Migrasi Database

Baca [CONTRIBUTING bagian 7](../CONTRIBUTING.md#7-alur-menambah-migrasi-database).
Intinya: buat berkas baru, jangan pernah mengubah migrasi yang sudah pernah
dijalankan.

### Menambah Pustaka Baru

Baca [CONTRIBUTING bagian 9](../CONTRIBUTING.md#9-menambah-library-baru).
Kalau pustaka itu menyentuh lapisan domain, bungkus di balik port.

### Mengirim Perubahan

Baca [CONTRIBUTING bagian 5](../CONTRIBUTING.md#5-format-commit) untuk format
commit, dan bagian 8 untuk checklist sebelum membuka pull request. Ada juga
templat pull request di `.github/PULL_REQUEST_TEMPLATE.md`.

---

## 10. Kalau Bingung

| Pertanyaan | Buka |
|---|---|
| Ada di mana ini? | [`docs/peta-kode.md`](peta-kode.md) |
| Apa arti istilah ini? | [`docs/glossary.md`](glossary.md) |
| Kenapa begini, bukan begitu? | [`docs/adr/`](adr/) |
| Ada yang error, apa yang harus saya lakukan? | [`docs/runbook.md`](runbook.md) |
| Pustaka ini untuk apa? | [`docs/libraries.md`](libraries.md) |
| Apa yang dilindungi dan apa yang tidak? | [`docs/security.md`](security.md) |
| Alur kontribusi dan konvensi kode | [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
