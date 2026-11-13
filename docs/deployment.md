# Panduan Deployment

Dokumen ini berisi hal-hal yang perlu diperhatikan saat layanan dijalankan di
server sungguhan. Untuk pengembangan lokal, cukup ikuti bagian
[Menjalankan](../README.md#menjalankan) di README.

---

## 1. Manajemen Secret

### Aturan utama

**Secret tidak boleh pernah ada di disk server.** Berkas `.env` itu untuk
pengembangan lokal saja. Di produksi, secret disuplai lewat environment
variable dari sistem yang menyimpan rahasia.

Aplikasi sudah dirancang supaya tidak butuh berkas `.env` sama sekali.
Baris `spring.config.import: optional:file:.env[.properties]` memakai
`optional:`, jadi kalau berkasnya tidak ada, aplikasi tetap jalan dengan
environment variable.

| Secret | Environment variable | Sumber yang disarankan |
|---|---|---|
| Kunci penanda tangan token | `JWT_SECRET` | Secret manager atau CI/CD |
| Kata sandi database | `POSTGRES_PASSWORD` | Secret manager atau CI/CD |
| Nama dan pengguna database | `POSTGRES_DB`, `POSTGRES_USER` | Environment variable biasa |
| Host dan port database | `POSTGRES_HOST`, `POSTGRES_PORT` | Environment variable biasa |
| Origin frontend | `CORS_ALLOWED_ORIGINS` | Environment variable biasa |

Variabel observability tidak bersifat rahasia, tetapi tetap dibaca dari
environment agar nilainya bisa berbeda per lingkungan:

| Variabel | Bawaan | Keterangan |
|---|---|---|
| `OTEL_TRACES_SAMPLING_PROBABILITY` | `0.0` | Peluang sampling tracing. `0.0` berarti tidak ada rentang yang dibuat |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Alamat collector OTLP/HTTP penerima rentang |

### Kenapa aplikasi gagal dengan cepat kalau secret hilang

Kalau `JWT_SECRET` tidak diisi, placeholder `${JWT_SECRET}` tidak bisa
dipecahkan dan aplikasi berhenti saat start dengan pesan yang jelas. Itu
memang perilaku yang diinginkan. Aplikasi yang gagal start lebih aman daripada
aplikasi yang jalan dengan kunci default atau kunci lemah.

Kalau `JWT_SECRET` diisi tapi kurang dari 32 byte, aplikasi juga berhenti saat
start, dengan pesan yang menyebutkan panjang yang diwajibkan.

### Opsi implementasi

#### Opsi A - Environment variable dari CI/CD

Paling sederhana dan cukup untuk layanan pada satu server.

```yaml
# .github/workflows/deploy.yml
env:
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
  POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}
```

Buat secret lewat **Settings → Secrets and variables → Actions**, lalu isi
nilai hasil `openssl rand -base64 48`.

**Kekurangan:** secret tidak bisa dirotasi tanpa deploy ulang, dan tidak ada
catatan siapa yang pernah membacanya.

#### Opsi B - Secret manager cloud

Untuk produksi yang lebih serius. Aplikasi membaca secret dari
AWS Secrets Manager, GCP Secret Manager, atau Azure Key Vault memakai pustaka
resmi Spring Cloud.

**Keunggulan:** ada catatan audit, rotasi bisa otomatis, dan hak akses bisa
dibatasi per service.

**Kekurangan:** menambah dependensi dan infrastruktur. Aplikasi jadi gagal
start kalau secret manager sedang tidak terjangkau.

#### Opsi C - Vault

Sama seperti Opsi B, memakai HashiCorp Vault. Cocok kalau infrastrukturnya
sudah memakai Vault untuk hal lain.

### Rotasi kunci token

Mengganti `JWT_SECRET` membuat seluruh token yang sudah terbit tidak berlaku
lagi. Semua pengguna dipaksa login ulang.

Kalau itu tidak diinginkan, diperlukan dukungan dua kunci sekaligus: token
baru ditandatangani dengan kunci baru, sedangkan token lama masih diterima
untuk verifikasi selama masa transisi. JWT menyediakan kolom header `kid`
(key id) untuk menandai kunci mana yang dipakai.

Fitur ini belum ada di layanan sekarang. Prioritasnya bergantung pada seberapa
sering kunci perlu dirotasi.

---

## 2. Profil Spring Boot

Untuk sementara, profil bawaan adalah `dev`. Itu berarti:

- `DevAdminBootstrap` aktif, sehingga ada akun administrator dengan kata
  sandi bawaan.
- Data contoh dari migrasi `db/migration-dev` ikut dimuat.

Keduanya **hanya cocok untuk pengembangan**. Sebelum layanan dibuka ke publik,
pastikan:

```bash
# di server
export SPRING_PROFILES_ACTIVE=prod
```

Nilai environment variable ini lebih tinggi prioritasnya daripada
`spring.profiles.active` di berkas konfigurasi, jadi cukup satu baris itu
saja.

Cara yang lebih aman adalah mengubah nilai bawaan di `application.yaml`
menjadi `prod`, lalu profil `dev` dipilih secara eksplisit saat
mengembangkan:

```bash
# Linux dan macOS
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

Profil `prod` memakai port `9090`, sedangkan profil `dev` memakai `8080`.
Nilainya ada di `application-prod.yaml`; kalau butuh port lain, timpa lewat
environment variable `SERVER_PORT`.

---

## 3. Bagian yang Dibuka ke Publik

| Endpoint | Akses | Catatan |
|---|---|---|
| `/actuator/health` | Publik | Isinya tidak sensitif karena detail komponen tidak ditampilkan |
| `/swagger-ui.html` | Publik | Hanya tersedia di profil dev; springdoc dimatikan di profil produksi |
| `/v3/api-docs` | Publik | Sama seperti Swagger UI: hidup di dev, dimatikan di produksi. Jangan bergantung pada role untuk menutupnya |
| `/auth/login` | Publik | Wajib ada, tapi perlu rate limit dan kunci akun |

---

## 4. Rate Limit

Rate limiter bawaan menyimpan penghitungnya di memori proses. Kalau layanan
dijalankan pada lebih dari satu instance, setiap instance punya penghitung
sendiri sehingga batas efektifnya menjadi beberapa kali lebih longgar.

Mitigasi yang sudah ada adalah kunci akun: percobaan login dihitung per akun,
sehingga penyebaran ke banyak alamat IP tidak membantu.

Kalau butuh batas yang benar-benar global, taruh rate limit di gateway atau
reverse proxy:

```nginx
limit_req_zone $binary_remote_addr zone=auth_zone:10m rate=20r/m;

location /auth/ {
    limit_req zone=auth_zone burst=5 nodelay;
    proxy_pass http://iam-service;
}
```

### 4.1 Alamat klien di belakang proxy

Rate limiter memakai alamat klien dari `request.getRemoteAddr()`. Aplikasi sudah
mengatur `server.forward-headers-strategy: native`, sehingga container hanya
memakai `X-Forwarded-For` bila koneksi datang dari proxy di jaringan internal.

Dua syarat harus dipenuhi:

1. Layanan **tidak boleh** bisa dijangkau langsung dari jaringan publik. Kalau
   penyerang bisa menghubungi aplikasi tanpa lewat proxy, header itu bisa
   dipalsukan.
2. Kalau proxy memakai alamat publik, daftarkan alamatnya di
   `server.tomcat.remoteip.internal-proxies`. Nilai bawaan hanya mencakup
   jaringan privat.

Kalau syarat itu tidak bisa dipenuhi, jangan andalkan limiter di aplikasi.
Tempatkan batas permintaan di gateway, seperti contoh nginx di atas.

### 4.2 Tugas latar

Pembersihan token kedaluwarsa dan pemindaian entri rate limiter berjalan di
dalam proses aplikasi. Pada deployment banyak instance, setiap instance
menjalankan pembersihan sendiri. Operasi hapusnya idempoten, jadi hasil
akhirnya tetap sama. Alasan dan tindak lanjutnya ada di
[ADR 0010](adr/0010-pemeliharaan-berkala-di-dalam-proses.md).

Penjadwal memakai dua thread, sehingga pemindaian rate limiter tidak menunggu
pembersihan token selesai.

---

## 5. Checklist Sebelum Buka ke Publik

- [ ] `SPRING_PROFILES_ACTIVE=prod` sudah disetel
- [ ] `JWT_SECRET` sudah diganti dengan nilai acak, minimal 32 byte
- [ ] `JWT_SECRET` tidak ada di berkas mana pun di server
- [ ] `POSTGRES_PASSWORD` sudah diganti dan tidak ada di berkas mana pun
- [ ] Port database tidak terbuka ke internet
- [ ] HTTPS sudah aktif, sertifikat sudah valid
- [ ] Database produksi tidak berisi akun bawaan `admin` dari profil dev
- [ ] `CORS_ALLOWED_ORIGINS` sudah diisi dengan domain yang benar
- [ ] Layanan hanya bisa dijangkau lewat proxy internal, atau batas permintaan
      dipindahkan sepenuhnya ke gateway
- [ ] GitHub Actions hijau pada commit terakhir
- [ ] Image container berhasil dibangun dan dijalankan sebagai pengguna tanpa hak istimewa
- [ ] Cadangan database terjadwal dan sudah pernah diuji memulihkannya
- [ ] Endpoint collector tracing diisi kalau tracing dinyalakan, atau sampling dibiarkan `0.0`

Akun `admin` hanya dibuat oleh `DevAdminBootstrap` pada profil `dev`. Kalau
database produksi berasal dari database pengembangan yang pernah dijalankan
dengan profil `dev`, akun itu bisa ikut terbawa. Karena itu yang diperiksa
adalah isi database-nya, bukan hanya nilai `SPRING_PROFILES_ACTIVE`.

---

## 6. Bentuk Artefak

Ada dua bentuk artefak yang bisa dipakai. Keduanya menjalankan kode yang sama;
yang berbeda hanya cara membungkusnya.

### 6.1 Berkas jar

Build menghasilkan satu berkas jar yang dijalankan dengan JDK 21:

```bash
java -jar target/iam-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Cocok kalau server sudah dikelola sendiri dan sudah punya JDK.

### 6.2 Image container

`Dockerfile` memakai dua tahap:

| Tahap | Isi | Kenapa |
|---|---|---|
| `build` | JDK 21, Maven Wrapper, seluruh sumber | Kompilasi dan pengemasan tidak membutuhkan JDK di mesin tujuan |
| jalan | JRE 21 dan satu berkas jar | Image lebih kecil dan tidak membawa compiler |

Ciri image yang dihasilkan:

- Berjalan sebagai pengguna `10001:10001`, bukan root.
- Profil `prod` sudah dipilih lewat `SPRING_PROFILES_ACTIVE=prod`, sehingga
  bootstrap administrator dev tidak pernah aktif.
- Port yang dibuka `9090`, mengikuti `application-prod.yaml`.
- `JAVA_TOOL_OPTIONS` bisa dipakai untuk mengatur memori atau properti JVM
  tanpa mengubah `ENTRYPOINT`.

```bash
docker build -t iam-service:latest .

docker run --rm -p 9090:9090 \
  -e JWT_SECRET="$JWT_SECRET" \
  -e POSTGRES_HOST=host.docker.internal \
  -e POSTGRES_PORT=5433 \
  -e POSTGRES_DB=iam_db \
  -e POSTGRES_USER=iam_user \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  iam-service:latest
```

`docker-compose.yml` di repositori **hanya menjalankan PostgreSQL**, bukan
layanan ini. Kalau ingin menjalankan keduanya sekaligus, tambahkan layanan
`iam-service` pada berkas compose dengan `image` hasil build di atas.

GitHub Actions membangun image pada setiap push dan pull request, tetapi tidak
mendorongnya ke registri mana pun. Repositori ini belum memiliki registri
rilis.

| Hal | Keadaan sekarang | Yang perlu disiapkan |
|---|---|---|
| Runtime | Jar butuh JDK 21; image sudah membawa JRE | Pilih salah satu bentuk artefak |
| Image container | Tersedia lewat `Dockerfile` | Bangun dan tandai versi sebelum dipakai |
| Registri image | Belum ada | Tambahkan registri kalau deployment memakai image |
| Cadangan database | Skrip tersedia di `scripts/` | Jadwalkan dan uji pemulihannya |
| Pemindaian kerentanan | Dependency review memeriksa perubahan dependensi di PR; Dependabot alerts memantau dependensi yang sudah ada | Tinjau peringatan secara berkala |

GitHub Actions menyimpan jar sebagai artifact selama 30 hari. Itu bukan
registri rilis: kalau butuh jejak jangka panjang, simpan jar atau image di
tempat lain, atau buat rilis bertanda di repositori.

---

## 7. Cadangan dan Pemulihan

Dua skrip tersedia di `scripts/`:

| Skrip | Fungsi |
|---|---|
| `backup-postgres.sh` | Membuat cadangan format custom dengan `pg_dump` |
| `restore-postgres.sh` | Memulihkan cadangan dan menimpa objek dengan nama sama |

Keduanya bisa memakai `pg_dump`/`pg_restore` yang terpasang di mesin, atau
menjalankannya di dalam container database. Mode container lebih praktis kalau
server hanya punya Docker:

```bash
export BACKUP_DOCKER_CONTAINER=iam-service-db
export POSTGRES_DB=iam_db
export POSTGRES_USER=iam_user
export POSTGRES_PASSWORD=rahasia

scripts/backup-postgres.sh cadangan/iam-$(date +%Y%m%d).dump
scripts/restore-postgres.sh cadangan/iam-20261113.dump --yes
```

Hal yang perlu diperhatikan:

- `restore-postgres.sh` menolak berjalan tanpa `--yes`, karena pemulihan
  menimpa objek yang namanya sama.
- Berkas cadangan berisi seluruh data, termasuk hash kata sandi. Simpan di
  tempat yang aksesnya terbatas dan enkripsi sebelum dipindahkan keluar server.
- Skrip mencetak SHA-256 berkas hasil cadangan. Simpan nilainya kalau perlu
  memeriksa keutuhan berkas sebelum pemulihan.
- Uji pemulihan ke database terpisah secara berkala. Cadangan yang belum pernah
  dipulihkan belum terbukti bisa dipakai.
- Contoh jadwal harian dengan cron:

  ```cron
  0 2 * * * cd /srv/iam-service && BACKUP_DOCKER_CONTAINER=iam-service-db \
    POSTGRES_DB=iam_db POSTGRES_USER=iam_user POSTGRES_PASSWORD=... \
    scripts/backup-postgres.sh /srv/cadangan/iam-$(date +\%Y\%m\%d).dump
  ```

---

## 8. Tracing Terdistribusi

Tracing memakai Micrometer Tracing dengan jembatan OpenTelemetry dan eksportir
OTLP/HTTP. Sampling **mati secara bawaan** (`0.0`) supaya aplikasi tidak
mencoba mengirim rentang ke collector yang belum tentu ada.

Untuk menyalakan:

```bash
export OTEL_TRACES_SAMPLING_PROBABILITY=0.1
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://collector:4318/v1/traces
```

| Nilai sampling | Arti |
|---|---|
| `0.0` | Tidak ada rentang yang dibuat. Ini nilai bawaan |
| `0.1` | Sekitar 10 persen permintaan dibuatkan rentang |
| `1.0` | Semua permintaan dibuatkan rentang. Hanya untuk diagnosis jangka pendek |

Saat tracing aktif, setiap baris log memuat `traceId` dan `spanId`, sehingga
satu baris log bisa dihubungkan dengan rentangannya di collector. Nilai itu
kosong saat sampling mati, jadi bentuk log biasa tidak berubah.

Layanan ini **hanya mengekspor tracing**. Metrik tetap dibaca lewat
`/actuator/metrics`; dependensi `micrometer-registry-otlp` sengaja tidak
dipakai agar metrik tidak ikut terkirim tanpa disadari.

Penolakan akses terhitung pada metrik `iam.security.rejected` dengan tag
`reason` (`unauthorized` atau `access_denied`) dan `method`. Di sistem yang
memakai Prometheus, nama metriknya muncul sebagai
`iam_security_rejected_total`.
