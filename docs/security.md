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
| Pencabutan sesi | Ganti kata sandi, reset kata sandi oleh admin, ganti role, dan nonaktifkan akun mencabut seluruh token refresh milik pengguna | Sesi tidak bisa diperpanjang. Access token yang sudah terbit tetap berlaku sampai kedaluwarsa, paling lama 15 menit |
| Pembersihan token kedaluwarsa | Baris dengan `expires_at` yang sudah lewat dihapus berkala setiap jam | Membatasi pertumbuhan tabel dan menjaga pencarian token tetap cepat. Token yang dicabut tetap disimpan sampai kedaluwarsa |
| Validasi penerbit | Token dari penerbit lain ditolak | Mencegah token dari sistem lain dipakai di sini |
| Validasi masa berlaku | Ditolak kalau sudah kedaluwarsa | Token lama tidak berlaku |
| Penolakan pengguna tidak dikenal | Pesan identik dengan kata sandi salah | Mencegah enumerasi username yang terdaftar |

### 1.2 Kontrol Akses

| Kontrol | Detail |
|---|---|
| Aturan per URL | Di `SecurityConfig`. Daftar pengguna hanya untuk ADMIN dan AUDITOR |
| Aturan per method | `@PreAuthorize`. Hanya pemilik akun yang boleh mengganti kata sandinya sendiri |
| Peran dari token | Claim `roles` dipetakan jadi `ROLE_*` oleh `JwtAuthenticationConverter` |
| Ditolak dengan jelas | 401 untuk belum terautentikasi, 403 untuk tidak berwenang |

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

Catatan untuk aksi gagal ditulis di transaksi terpisah, supaya tidak hilang
saat transaksinya dibatalkan.

---

## 2. Apa yang BELUM Dilindungi

Bagian ini sengaja ditulis, supaya tidak ada yang mengira layanan ini sudah
sempurna.

| Celah | Dampak | Mitigasi sekarang |
|---|---|---|
| **Rate limiter per instance** | Kalau dijalankan lebih dari satu instance, batas efektifnya beberapa kali lebih longgar | Kunci akun per akun tetap bekerja. Entri klien yang tidak aktif dibuang berkala supaya memori tidak tumbuh, tetapi batas global harus ditaruh di gateway |
| **Rate limiter bergantung pada alamat klien** | Kalau layanan bisa dijangkau langsung, header `X-Forwarded-For` bisa dipalsukan untuk lolos dari batas | `server.forward-headers-strategy=native` hanya memakai header itu bila koneksi datang dari proxy internal. Pastikan layanan tidak terbuka langsung |
| **Belum ada deteksi penyalahgunaan refresh token** | Kalau token refresh dicuri, pemakai baru bisa memakainya sampai token dicabut atau kedaluwarsa | Rotasi membuat token lama tidak bisa dipakai lagi |
| **Rotasi kunci token memutus seluruh sesi** | Mengganti `JWT_SECRET` membuat semua pengguna logout paksa | Belum ada dukungan dua kunci bersamaan dengan header `kid` |
| **Tidak ada refresh token per perangkat** | Satu token refresh berlaku di semua perangkat pengguna | Untuk menghapus sesi di satu perangkat, cabut tokennya manual |
| **Belum ada audit log yang tidak bisa diubah** | Seseorang yang punya akses database bisa mengubah catatan audit | Simpan catatan audit di tempat terpisah kalau sudah dibutuhkan |
| **Belum ada rate limit pada endpoint lain** | Endpoint selain autentikasi tidak dibatasi | Penyerang yang sudah terautentikasi bisa mengirim banyak permintaan |
| **Belum ada multi-tenancy** | Satu database untuk semua organisasi | Gunakan database terpisah per organisasi kalau sudah perlu |
| **Belum ada SAML atau OIDC** | Layanan ini hanya bisa dipakai dengan token sendiri | Integrasi ke identity provider lain belum ada |

---

## 3. Model Ancaman Ringkas

| Penyerang | Serangan | Kontrol yang menahan |
|---|---|---|
| Penyerang luar | Tebakan kata sandi | Rate limit, kunci akun, bcrypt |
| Penyerang luar | Memakai token yang dicuri | Masa berlaku access token pendek, rotasi refresh token, pencabutan sesi |
| Penyerang luar | Mengenumerate username | Pesan galat identik |
| Pengguna biasa | Membaca daftar pengguna | Aturan URL: hanya ADMIN dan AUDITOR |
| Pengguna biasa | Mengubah akun orang lain | Aturan method: hanya pemilik akun atau ADMIN |
| Pengguna biasa | Menaiki peran | Aturan URL dan `@PreAuthorize`: hanya ADMIN yang boleh mengganti role |
| Penyebab kebocoran | Memakai token dari penerbit lain | Validasi claim `iss` |
| Penyebab kebocoran | Membaca hash kata sandi dari database | Hash memakai BCrypt dengan salt, dan tidak pernah dikembalikan lewat API |

---

## 4. Apa yang Harus Dijaga Saat Deploy

- [ ] `JWT_SECRET` acak, minimal 32 byte, **tidak** ada di berkas mana pun di server
- [ ] `JWT_SECRET` dirotasi kalau pernah bocor
- [ ] `SPRING_PROFILES_ACTIVE=prod` supaya profil dev dan bootstrap administrator tidak aktif
- [ ] Port database tidak terbuka ke internet
- [ ] HTTPS aktif, sertifikat valid
- [ ] `CORS_ALLOWED_ORIGINS` diisi dengan domain yang benar
- [ ] Dokumentasi OpenAPI **mati** di produksi, sudah dikonfigurasi secara bawaan
- [ ] Akun administrator dev sudah dihapus atau kata sandinya diganti

Rinciannya ada di [`deployment.md`](deployment.md).

---

## 5. Pelaporan Kerentanan

Lihat [`SECURITY.md`](../SECURITY.md) di root repositori.
