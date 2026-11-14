# Model Keamanan

Dokumen ini merangkum kontrol keamanan yang ada di layanan ini, alasan
dipilihnya, dan **batasannya**. Bagian batasannya penting: layanan yang
menganggap dirinya aman tanpa menyebutkan batasnya bisa mengecewakan.

---

## 1. Apa yang Dilindungi

Layanan ini mengelola identitas dan akses. Kalau diretas, penyerang mendapat
akses ke seluruh akun yang ada. Karena itu kontrolnya berlapis.

### 1.1 Autentikasi

| Kontrol | Detail | Kenapa |
|---|---|---|
| Token JWT HS256 | Access token 15 menit, refresh token 7 hari | Access token pendek membatasi dampak kalau bocor |
| Refresh token disimpan sebagai hash SHA-256 | Yang tersimpan hanya hash-nya | Kebocoran database tidak langsung berarti kebocoran token |
| Rotasi refresh token | Token lama dicabut begitu ditukar | Token yang dicuri hanya bisa dipakai sekali |
| Sesi per perangkat | Setiap login membuka satu baris `sessions` dengan id sendiri. Token akses dan token refresh membawa klaim `session_id` yang menunjuk baris itu | Satu perangkat bisa dicabut tanpa mematikan perangkat lain |
| Deteksi pemakaian ulang | Token yang dicabut oleh rotasi, sementara sesinya masih hidup, lalu dipakai lagi dianggap bukti pencurian. Seluruh token refresh dan sesi pengguna dicabut dan kejadiannya dicatat sebagai `REFRESH_TOKEN_REUSE` | Pencuri yang memegang token hasil rotasi ikut kehilangan akses. Pemilik sah harus login ulang |
| Pencabutan satu sesi | Logout mencabut sesi pada token yang dikirim beserta seluruh token refresh di dalamnya. `DELETE /v1/auth/sessions/{id}` melakukan hal yang sama untuk sesi lain milik pemanggil. Sesi milik pengguna lain dijawab 404 | Perangkat yang hilang bisa dimatikan tanpa mengganggu perangkat lain. Jawaban yang sama mencegah penebakan id sesi |
| Daftar perangkat | `GET /v1/auth/sessions` hanya menampilkan sesi aktif milik pemanggil, dengan penanda `current` pada sesi yang tokennya dipakai | Pemilik akun bisa mengenali perangkatnya sendiri sebelum mencabut |
| Pencabutan sesi | Ganti kata sandi, reset kata sandi oleh admin, ganti role, dan nonaktifkan akun mencabut seluruh token refresh dan seluruh sesi milik pengguna. Barisnya ditandai `revoked_at`, tidak dihapus, lalu dibersihkan setelah kedaluwarsa | Sesi tidak bisa diperpanjang. Access token yang sudah terbit tetap berlaku sampai kedaluwarsa, paling lama 15 menit. Jejak pencabutan tetap ada untuk menelusuri pemakaian ulang |
| Pembersihan token kedaluwarsa | Baris dengan `expires_at` yang sudah lewat dihapus berkala setiap jam | Membatasi pertumbuhan tabel dan menjaga pencarian token tetap cepat. Token yang dicabut tetap disimpan sampai kedaluwarsa |
| Pembersihan sesi tidak aktif | Sesi yang dicabut atau tidak dipakai lebih lama dari masa berlaku token refresh dihapus berkala setiap jam | Daftar perangkat tidak menyimpan riwayat selamanya. Sesi seperti itu sudah tidak bisa dilanjutkan |
| Validasi penerbit | Token dari penerbit lain ditolak | Mencegah token dari sistem lain dipakai di sini |
| Validasi masa berlaku | Ditolak kalau sudah kedaluwarsa | Token lama tidak berlaku |
| Penolakan pengguna tidak dikenal | Pesan identik dengan kata sandi salah | Mencegah enumerasi username yang terdaftar |
| Rotasi kunci dengan dua kunci | Token membawa penanda kunci (`kid`). Kunci aktif dipakai untuk menerbitkan, kunci sebelumnya masih diterima untuk verifikasi | Rotasi normal tidak memaksa seluruh pengguna login ulang |
| Kunci sebelumnya hanya untuk verifikasi | Encoder selalu memakai kunci aktif, berapa pun kunci yang tersedia | Token baru tidak pernah terbit dengan kunci yang sudah digantikan |

### 1.2 Kontrol Akses

| Kontrol | Detail |
|---|---|
| Aturan per URL | Di `SecurityConfig`. Daftar pengguna hanya untuk ADMIN dan AUDITOR |
| Aturan per method | `@PreAuthorize`. Hanya pemilik akun yang boleh mengganti kata sandinya sendiri |
| Peran dari token | Claim `roles` dipetakan jadi `ROLE_*` oleh `JwtAuthenticationConverter` |
| Ditolak dengan jelas | 401 untuk belum terautentikasi, 403 untuk tidak berwenang |
| Penolakan tercatat | 403 menulis jejak audit `ACCESS_DENIED` beserta pelakunya. 401 dan 403 menaikkan log ke WARN dan menambah metrik `iam.security.rejected`. 401 juga ditambahkan ke agregat per menit di `security_rejection_stats` |
| Sesi selalu milik pemanggil | `/v1/auth/sessions` dan `/v1/auth/sessions/**` hanya butuh autentikasi, lalu use case memeriksa pemilik barisnya. Sesi milik pengguna lain diperlakukan seperti sesi yang tidak ada |

### 1.3 Kata Sandi

| Kontrol | Detail |
|---|---|
| Hash BCrypt | Salt otomatis, hash berbeda tiap kali untuk kata sandi yang sama |
| Panjang 8 sampai 72 karakter | Batas 72 dipilih agar tidak melewati batas BCrypt, yang hanya memakai 72 byte pertama. Pemeriksaan memakai jumlah karakter, jadi kata sandi berisi banyak karakter non-ASCII bisa melewati 72 byte dan kelebihannya diabaikan BCrypt |
| Variasi jenis karakter | Minimal satu huruf dan satu angka |
| Kunci akun | 5 kali gagal → terkunci 15 menit, dihitung **per akun** |

### 1.4 Bentuk Permintaan

| Kontrol | Detail |
|---|---|
| Validasi Bean Validation | DTO punya anotasi `@NotBlank`, `@Size`, `@Email`, `@Pattern` |
| Batas ukuran halaman | Maksimal 50 baris, ditegakkan di Spring Boot dan diperiksa pengujian regresi |
| Daftar properti pengurutan | Hanya `id`, `username`, `email`, `active`, `createdAt`, `updatedAt` yang diizinkan |

Batas panjang 72 karakter pada kata sandi dicek di dua tempat dengan
perbedaan yang disengaja:

| Situasi | Hasil | Kenapa |
|---|---|---|
| Kata sandi lebih dari 72 karakter saat **login** | 400 dengan rincian per field | Batas itu soal ukuran hash BCrypt, bukan aturan bisnis login |
| Kata sandi lebih dari 72 karakter saat **membuat pengguna** | 400 dengan rincian per field | Sama, `PasswordPolicy` juga menolak |
| Kata sandi **pendek** saat login | 401 dengan pesan kredensial salah | Panjang minimum hanya ditegakkan saat membuat pengguna, supaya login tidak membocorkan aturan |

Pesan 401 selalu sama untuk username yang salah dan kata sandi yang salah.
Kalau nama penggunanya tidak terdaftar, jawabannya juga 401 dengan pesan yang
sama persis.

### 1.5 Header Keamanan

| Header | Nilai | Sumber |
|---|---|---|
| `Content-Security-Policy` | `default-src 'self'` dan seterusnya. Sumber dari luar diblokir | `SecurityConfig` |
| `X-Frame-Options` | `DENY` | `SecurityConfig` |
| `X-Content-Type-Options` | `nosniff` | `SecurityConfig` |
| `Strict-Transport-Security` | Aktif, termasuk subdomain, 1 tahun | `SecurityConfig` |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` | Bawaan Spring Security |

Catatan untuk dua header terakhir:

- `Strict-Transport-Security` hanya dikirim pada permintaan HTTPS. Pada
  localhost dengan HTTP biasa, header ini memang tidak muncul. Itu perilaku
  yang benar, bukan tanda konfigurasi gagal.
- `Cache-Control` dipasang oleh Spring Security secara bawaan untuk **seluruh**
  respons, bukan hanya respons yang sensitif. Nilainya sudah memenuhi
  kebutuhan, jadi layanan tidak perlu mengatur header ini sendiri.

### 1.6 Jejak Audit

Semua perubahan penting dicatat: pembuatan pengguna, perubahan data, penggantian
role pengguna, pembuatan dan penghapusan role, aktivasi, penonaktifan,
penggantian kata sandi, login berhasil, login gagal, akun terkunci, logout, dan
penukaran token refresh.

Pencabutan token refresh secara massal juga menulis catatan audit tersendiri
dengan kode aksi `TOKENS_REVOKED`. Catatan itu dibuat per kejadian pemicu,
misalnya `USER_DEACTIVATED` atau `PASSWORD_CHANGED`, dan memuat aktor pemanggil,
pengguna target, serta jumlah token yang dicabut. Kejadian pemicunya tetap
dicatat terpisah, sehingga urutan "apa yang terjadi" dan "berapa sesi yang ikut
mati" bisa dibaca berdampingan.

Pencabutan lewat `DELETE /v1/auth/sessions/{id}` dicatat sebagai
`SESSION_REVOKED` beserta id sesinya. Logout tidak menulis aksi itu, karena
`LOGOUT` sudah menandai kejadiannya.

Catatan untuk aksi gagal ditulis di transaksi terpisah, supaya tidak hilang
saat transaksinya dibatalkan.

Penolakan akses ditangani dengan aturan yang berbeda, dan perbedaannya
disengaja:

| Kejadian | Log | Metrik | Agregat | Jejak audit |
|---|---|---|---|---|
| 401, tidak ada token atau token tidak sah | WARN | `iam.security.rejected`, tag `reason=unauthorized` | `security_rejection_stats` per menit, method, dan alasan | **Tidak ditulis** |
| 403, identitas sah tetapi tidak berwenang | WARN | `iam.security.rejected`, tag `reason=access_denied` | Tidak | `ACCESS_DENIED` beserta `actor_user_id` dan URI |

Alasan 401 tidak masuk jejak audit: permintaan tanpa kredensial bisa dikirim
siapa saja tanpa identitas. Kalau setiap permintaan seperti itu menulis satu
baris, tabel audit bisa dibanjiri trafik luar dan catatan yang penting justru
tenggelam. Untuk 403, identitas pelakunya sudah terbukti sah, sehingga
kejadiannya justru perlu bisa ditelusuri sampai ke pengguna.

Agregat 401 menyimpan alasannya dalam dua kode pendek: `missing_credentials`
kalau header `Authorization` tidak ada, dan `invalid_token` kalau header ada
tetapi tokennya ditolak. Alasannya sengaja **tidak** memuat path maupun alamat
IP. Keduanya dikendalikan pemanggil, sehingga penyerang bisa membuat tabel
membengkak hanya dengan mengirim path acak. Yang tetap terjawab oleh agregat
ini adalah "kapan lonjakan terjadi, pada method apa, dan karena alasan apa".
Detail per permintaan tetap ada di log, yang tidak disimpan di database.

Penyimpanan jejak audit penolakan tidak boleh menggagalkan respons. Kalau
database sedang bermasalah, pemanggil tetap menerima 403, bukan 500. Aturan
yang sama berlaku untuk agregat 401: kegagalan menulis agregat hanya dicatat
di log, dan pemanggil tetap menerima 401.

#### Rantai hash

Setiap baris `audit_logs` menyimpan `prev_hash` dan `entry_hash`. Hash dihitung
dari `prev_hash` ditambah seluruh isi baris, sehingga perubahan satu baris
membuat hash-nya tidak cocok lagi, dan penghapusan satu baris membuat
penerusnya kehilangan pendahulu.

Perhitungan dilakukan di database, bukan di aplikasi:

- Trigger `BEFORE INSERT` mengisi kedua kolom. Jalur tulis apa pun, termasuk
  skrip pemeliharaan, tetap menghasilkan rantai yang sama.
- Trigger mengambil kunci penasihat tingkat transaksi sebelum membaca ujung
  rantai. Dua penulisan bersamaan menjadi berurutan, sehingga tidak ada dua
  baris yang mengaku sebagai ujung yang sama.
- Rumusnya dipusatkan di fungsi `audit_log_hash` dan dipakai juga oleh backfill,
  sehingga tidak ada dua rumus yang bisa menyimpang.

Empat kelas hasil verifikasi:

| Kode | Arti |
|---|---|
| `HASH_MISMATCH` | Isi baris berubah setelah hash dihitung |
| `MISSING_PREDECESSOR` | Baris yang ditunjuk `prev_hash` sudah tidak ada |
| `MULTIPLE_HEADS` | Lebih dari satu baris mengaku sebagai awal rantai |
| `UNREACHABLE_ENTRY` | Baris tidak bisa dicapai dari awal rantai, biasanya karena siklus atau pemutusan |

Verifikasi memakai keterkaitan hash, bukan urutan `id`. Alasannya, `id` sudah
dibuat sebelum kunci transaksi diperoleh, sehingga pada penulisan yang
benar-benar bersamaan urutan rantai bisa berbeda dari urutan `id`.

Endpoint `GET /v1/audit-logs/verify` hanya untuk ADMIN. Hasil pemeriksaan
memuat id entri rusak, dan itu adalah petunjuk langsung bagi siapa pun yang
ingin menyembunyikan jejaknya. AUDITOR tetap bisa membaca daftar audit biasa,
tetapi tidak melihat detail kerusakan.

Yang **tidak** dicakup rantai ini: pemegang akses tulis ke database masih bisa
menghitung ulang seluruh rantai dari awal. Rantai membuat perubahan itu harus
disengaja dan sulit disembunyikan, bukan mustahil. Untuk jaminan yang lebih
kuat, salinan berkala perlu dikirim ke penyimpanan append-only di luar database.

### 1.7 Pemindaian Dependensi dan Analisis Statis

| Pemeriksaan | Kapan berjalan | Cakupan |
|---|---|---|
| `dependency-review-action` | Setiap pull request ke `main` | Dependensi yang ditambahkan atau diperbarui. Build gagal kalau ada kerentanan High |
| Dependabot | Mingguan | Pembaruan dependensi Maven dan GitHub Actions |
| Checkstyle | Setiap `mvn verify` | Impor, tab, akhir berkas, panjang baris 120, kurung kurawal wajib, satu pernyataan per baris, urutan modifier |
| SpotBugs | Setiap `mvn verify` | Temuan berprioritas Medium ke atas dengan effort maksimal, memakai pengecualian sempit yang terdokumentasi |
| JaCoCo | Setiap `mvn verify` | Cakupan baris minimal 80 persen untuk setiap kelas |
| CycloneDX | Setiap `mvn verify` | SBOM seluruh komponen di `target/bom.json` |
| Trivy | Setiap build CI dan mingguan | Kerentanan HIGH dan CRITICAL pada image, hanya yang sudah punya perbaikan |

Dependency review hanya memeriksa perubahan pada pull request, bukan seluruh
pohon dependensi yang sudah ada. Untuk dependensi lama, Trivy memeriksa image
yang benar-benar dikirim, dan peringatan Dependabot tetap ditinjau.

---

## 2. Apa yang BELUM Dilindungi

Bagian ini sengaja ditulis, supaya tidak ada yang mengira layanan ini sudah
sempurna.

| Celah | Dampak | Mitigasi sekarang |
|---|---|---|
| **Rate limiter per instance** | Kalau dijalankan lebih dari satu instance, batas efektifnya beberapa kali lebih longgar | Kunci akun per akun tetap bekerja. Entri klien yang tidak aktif dibuang berkala supaya memori tidak tumbuh, tetapi batas global harus ditaruh di gateway |
| **Rate limiter bergantung pada alamat klien** | Kalau layanan bisa dijangkau langsung, header `X-Forwarded-For` bisa dipalsukan untuk lolos dari batas | `server.forward-headers-strategy=native` hanya memakai header itu bila koneksi datang dari proxy internal. Pastikan layanan tidak terbuka langsung |
| **Pemakaian ulang memaksa login ulang** | Percobaan ulang setelah respons jaringan hilang bisa dianggap pencurian, sehingga seluruh sesi pengguna dicabut | Ini konsekuensi yang dipilih. Tanpa tenggang waktu, deteksi tetap tegas; pengguna cukup login ulang |
| **Rotasi kunci mengharuskan restart serentak** | Instance yang belum menerima kunci baru menolak token yang ditandatangani kunci baru | Pastikan semua instance memakai konfigurasi yang sama. Pada deployment bergulir, sebagian permintaan bisa gagal sementara |
| **Pencabutan sesi tidak membatalkan access token** | Access token yang sudah terbit tetap berlaku sampai kedaluwarsa, paling lama 15 menit | Masa berlaku pendek adalah kontrolnya. Pencabutan access token secara langsung butuh daftar cabut terpusat, yang belum ada |
| **Label perangkat berasal dari klien** | Nama perangkat bisa dipalsukan atau kosong, sehingga dua perangkat tampak sama | Label hanya alat bantu pemilik akun, bukan identitas. Pencabutan tetap memakai id sesi yang dibuat server |
| **Agregat 401 berbutir satu menit** | Lonjakan dalam menit yang sama digabung, dan path maupun alamat IP tidak disimpan | Log WARN memuat path dan tetap tersedia untuk penelusuran. Batas ini sengaja dipilih supaya tabel tidak bisa dibengkakkan pemanggil |
| **Riwayat sesi ikut terhapus** | Setelah pembersihan, sesi lama tidak lagi terlihat di daftar perangkat | Jejak pencabutan ada di `audit_logs` selama retensi audit, bukan di tabel sesi |
| **Rantai hash bukan jaminan mutlak** | Rantai mendeteksi perubahan dan penghapusan baris, tetapi pemegang akses tulis database masih bisa menghitung ulang seluruh rantai | Kirim salinan berkala ke penyimpanan append-only di luar database |
| **Belum ada rate limit pada endpoint lain** | Endpoint selain autentikasi tidak dibatasi | Penyerang yang sudah terautentikasi bisa mengirim banyak permintaan |
| **401 tidak disimpan ke jejak audit** | Percobaan tanpa kredensial tidak bisa ditelusuri sampai pelaku | Log WARN, metrik `iam.security.rejected`, dan agregat per menit tetap tersedia. Alasan lengkapnya ada di bagian 1.6 |
| **`/actuator/prometheus` tanpa token** | Scraper bisa membaca metrik tanpa autentikasi. Metrik itu tidak sensitif, tapi mencerminkan volume lalu lintas | Endpoint wajib hanya bisa dijangkau jaringan internal scraper. Jangan publiekan melalui gateway publik |
| **Dependency review hanya memeriksa perubahan** | Kerentanan pada dependensi lama tidak menggagalkan pull request | Dependabot alerts dan peninjauan berkala; jalankan pemindai penuh sebelum rilis besar |
| **Analisis statis belum mencakup Low** | Temuan berprioritas Low belum memblokir build | Ambang Medium sudah berlaku; pengecualian ditulis per kelas dan pola, bukan mematikan pola global |
| **Belum ada multi-tenancy** | Satu database untuk semua organisasi | Gunakan database terpisah per organisasi kalau sudah perlu |
| **Belum ada SAML atau OIDC** | Layanan ini hanya bisa dipakai dengan token sendiri | Integrasi ke identity provider lain belum ada |

---

## 3. Model Ancaman Ringkas

| Penyerang | Serangan | Kontrol yang menahan |
|---|---|---|
| Penyerang luar | Tebakan kata sandi | Rate limit, kunci akun, bcrypt |
| Penyerang luar | Memakai token yang dicuri | Masa berlaku access token pendek, rotasi refresh token, deteksi pemakaian ulang, pencabutan sesi |
| Penyerang luar | Memakai ulang refresh token hasil rotasi | Deteksi pemakaian ulang mencabut seluruh token refresh dan sesi pengguna |
| Pengguna biasa | Melihat atau mencabut sesi pengguna lain | Sesi disaring berdasarkan pemilik, dan sesi milik orang lain dijawab 404 tanpa membedakannya dari sesi yang tidak ada |
| Penyerang luar | Mengenumerate username | Pesan galat identik |
| Pengguna biasa | Membaca daftar pengguna | Aturan URL: hanya ADMIN dan AUDITOR |
| Pengguna biasa | Mengubah akun orang lain | Aturan method: hanya pemilik akun atau ADMIN |
| Pengguna biasa | Menaiki peran | Aturan URL dan `@PreAuthorize`: hanya ADMIN yang boleh mengganti role |
| Penyebab kebocoran | Memakai token dari penerbit lain | Validasi claim `iss` |
| Penyebab kebocoran | Membaca hash kata sandi dari database | Hash memakai BCrypt dengan salt, dan tidak pernah dikembalikan lewat API |

---

## 4. Apa yang Harus Dijaga Saat Deploy

- [ ] `JWT_SECRET` acak, minimal 32 byte, **tidak** ada di berkas mana pun di server
- [ ] `JWT_SECRET` dirotasi kalau pernah bocor, mengikuti prosedur rotasi di `deployment.md`
- [ ] `JWT_SECRET_PREVIOUS` kosong pada operasi normal, dan hanya diisi selama masa transisi rotasi
- [ ] `SPRING_PROFILES_ACTIVE=prod` supaya profil dev dan bootstrap administrator tidak aktif
- [ ] Port database tidak terbuka ke internet
- [ ] HTTPS aktif, sertifikat valid
- [ ] `CORS_ALLOWED_ORIGINS` diisi dengan domain yang benar
- [ ] Dokumentasi OpenAPI **mati** di produksi, sudah dikonfigurasi secara bawaan
- [ ] Database produksi tidak berisi akun bawaan `admin` dari profil dev
- [ ] Image berjalan sebagai pengguna tanpa hak istimewa dan memakai profil `prod`
- [ ] Dependency review dan Dependabot alerts sudah ditinjau
- [ ] Retensi `iam.security-rejection-stats.retention` sesuai kebijakan penyimpanan setempat
- [ ] Pembersihan sesi dan agregat 401 menyala, atau dijadwalkan di luar aplikasi
- [ ] Endpoint collector tracing diisi kalau sampling dinyalakan

Rinciannya ada di [`deployment.md`](deployment.md).

---

## 5. Pelaporan Kerentanan

Lihat [`SECURITY.md`](../SECURITY.md) di root repositori.
