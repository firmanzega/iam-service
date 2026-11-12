# docs/security.md

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
| Pencabutan sesi | Ganti kata sandi, ganti role, dan nonaktifkan akun mencabut seluruh token refresh milik pengguna | Sesi lama langsung mati saat ada perubahan sensitif |
| Validasi penerbit | Token dari penerbit lain ditolak | Mencegah token dari sistem lain dipakai di sini |
| Validasi masa berlaku | Ditolak kalau sudah kedaluwarsa | Token lama tidak berlaku |
| Penolakan pengguna tidak dikenal | Pesan identik dengan kata sandi salah | Mencegah penyEnumerasi username yang terdaftar |

### 1.2 Kontrol Akses

| Kontrol | Detail |
|---|---|
| Aturan per URL | Di `SecurityConfig`. Daftar pengguna hanya untuk ADMIN dan AUDITOR |
| Aturan per method | `@PreAuthorize`. Hanya pemilik akun yang boleh mengganti kata sandinya sendiri |
| Peran dari token | Claim `roles` dipetakan jadi `ROLE_*` oleh `JwtAuthenticationConverter` |
| Ditolak dengan jelas | 401 untuk belum terautentikasi, 403 untuk tidak berwenang |

### 1.3 KATA SANDI

| Kontrol | Detail |
|---|---|
| Hash BCrypt | Salt otomatis, hash berbeda tiap kali untuk kata sandi yang sama |
| Panjang 8 sampai 72 karakter | BCrypt hanya memakai 72 byte pertama |
| Variasi jenis karakter | Minimal satu huruf dan satu angka |
| Kunci akun | 5 kali gagal -> terkunci 15 menit, dihitung **per akun** |

### 1.4 Bentuk Permintaan

| Kontrol | Detail |
|---|---|
| Validasi Bean Validation | DTO punya anotasi `@NotBlank`, `@Size`, `@Email`, `@Pattern` |
| Batas ukuran halaman | Maksimal 50 baris, ditegakkan di Spring Boot dan diperiksa pengujian regresi |
| Daftar properti pengurutan | Hanya `id`, `username`, `email`, `active`, `createdAt`, `updatedAt` yang diizinkan |

### 1.5 Header Keamanan

| Header | Nilai |
|---|---|
| `Content-Security-Policy` | `default-src 'self'` dan seterusnya. Sumber dari luar diblokir |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Strict-Transport-Security` | Aktif, termasuk subdomain, 1 tahun |
| `Cache-Control` | `no-store` pada respons sensitif |

### 1.6 Jejak Audit

Semua perubahan penting dicatat: pembuatan pengguna, perubahan data, penggantian
role, aktivasi, penonaktifan, penggantian kata sandi, login berhasil, login
gagal, akun terkunci, logout, dan pencabutan token.

Catatan untuk aksi gagal ditulis di transaksi terpisah, supaya tidak hilang
saat transaksinya dibatalkan.

---

## 2. Apa yang BELUM Dilindungi

Bagian ini sengaja ditulis, supaya tidak ada yang mengira layanan ini sudah
sempurna.

| Celah | Dampak | Mitigasi sekarang |
|---|---|---|
| **Rate limiter per instance** | Kalau dijalankan lebih dari satu instance, batas efektifnya beberapa kali lebih longgar | Kunci akun per akun tetap bekerja. Rate limit global harus ditaruh di gateway |
| **Belum ada deteksi penyalahgunaan refresh token** | Kalau token refresh dicuri, pemakai baru bisa memakainya sampai token dicabut atau kedaluwarsa | Rotasi membuat token lama tidak bisa dipakai lagi |
| **Rotasi kunci token memutus seluruh sesi** | Mengganti `JWT_SECRET` membuat semua pengguna logout paksa | Belum ada dukungan dua kunci bersamaan dengan header `kid` |
| **Tidak ada refresh token per perangkat** | Satu token refresh berlaku di semua perangkat pengguna | Untuk Uninstall berarti(Session) perangkat, revoke token manual |
| **Belum ada audit log yang tidak bisa diubah** | Seseorang yang punya akses database bisa mengubah catatan audit | Simpan catatan audit di tempat terpisah kalau sudah dibutuhkan |
| **Belum ada rate limit pada endpoint lain** | Endpoint selain autentikasi tidak dibatasi | Penyerang yang sudah terautentikasi bisa mengirim banyak permintaan |
| **Belum ada multi-tenancy** | Satu database untuk semua organisasi |-Gunakan database terpisah per organisasi kalau sudah perlu |
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
| Pengguna biasa | Menaiki peran | Penguncian optimistis pada field `version` |
| Penyebab kebocoran | Memakai token dari penerbit lain | Validasi claim `iss` |
| Penyebab kebocoran | Membaca hash kata sandi dari database | Refresh token disimpan terpisah dari hash kata sandi |

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
