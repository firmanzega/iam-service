# Glosarium

Kamus istilah yang dipakai di proyek ini. Tujuannya satu: supaya orang yang baru
membaca kode tidak menebak-nebak apa arti suatu kata.

Format tiap entri: istilah Indonesia, padanannya dalam bahasa Inggris, lalu
artinya dalam konteks layanan ini.

---

## 1. Identitas dan Akses

| Istilah | Inggris | Arti |
|---|---|---|
| IAM | Identity and Access Management | Layanan yang mengurus identitas pengguna dan hak aksesnya ke sistem lain |
| Peran | Role | Nama kelompok hak akses, misalnya `ADMIN` atau `AUDITOR`. Disimpan di tabel `roles` |
| Hak akses | Authority, GrantedAuthority | Bentuk izin spesifik yang dipakai Spring Security, selalu berawalan `ROLE_` |
| RBAC | Role-Based Access Control | Model akses berbasis peran: izin ikut peran, bukan melekat langsung ke pengguna |
| Penegakan aturan | Enforcement | Lapisan yang menolak permintaan yang tidak memenuhi aturan, ada di `SecurityConfig` dan `@PreAuthorize` |
| Penolakan | Denial | Permintaan yang tidak lolos aturan. Bedakan dua bentuknya: 401 kalau belum terautentikasi, 403 kalau sudah terautentikasi tapi tidak berwenang |

## 2. Token

| Istilah | Inggris | Arti |
|---|---|---|
| Token akses | Access token | Token berumur pendek, 15 menit, dipakai pada header `Authorization` untuk setiap permintaan |
| Token refresh | Refresh token | Token berumur panjang, 7 hari, satu-satunya cara memperpanjang sesi tanpa kata sandi |
| Rotasi token refresh | Refresh token rotation | Setiap kali token refresh ditukar, token lamanya langsung dicabut |
| Klaim | Claim | Isi token yang sudah ditandatangani, tidak bisa diubah tanpa merusak tanda tangan. Contohnya `sub`, `iss`, `roles`, `jti` |
| Identitas subjek | Subject claim | Klaim `sub`, pada layanan ini berisi id pengguna |
| Identitas penerbit | Issuer claim | Klaim `iss`, identitas yang menerbitkan token. Token dari penerbit lain ditolak |
| Pengenal unik | JWT ID | Klaim `jti`, pengenal acak tiap token. Wajib ada supaya dua token refresh tidak bentrok |
| Tanda tangan | Signature | Bukti keaslian token, dihitung dengan kunci HMAC HS256 |
| Masa berlaku | Time to live, TTL | Lamanya token masih diterima |
| Pencabutan | Revocation | Tanda bahwa token sudah tidak boleh dipakai, disimpan di tabel `refresh_tokens` |
| Pencabutan seluruh sesi | Revoke all | Mencabut semua token refresh milik satu pengguna sekaligus |
| Deteksi pemakaian ulang | Reuse detection | Mengenali token refresh yang sudah dicabut lalu dipakai lagi, mencabut seluruh sesi pengguna, dan mencatatnya sebagai `REFRESH_TOKEN_REUSE` |

## 3. Kata Sandi dan Kunci

| Istilah | Inggris | Arti |
|---|---|---|
| Hash | Hash | Hasil satu arah dari kata sandi. Tidak bisa dibalik jadi kata sandi asli |
| Garam | Salt | Data acak yang ikut disimpan bersama hash, supaya kata sandi sama menghasilkan hash berbeda tiap kali |
| Sidik jari | Fingerprint | Hash SHA-256 dari token mentah. Yang disimpan di database hanya sidik jarinya, bukan tokennya |
| BCrypt | - | Algoritma hash kata sandi yang lambat dengan sengaja, supaya tebakan brute force mahal |
| Kunci akun | Account lockout | Akun dinonaktifkan sementara setelah beberapa kali gagal login, supaya penyerang tidak bisa mencoba terus |
| Anti enumerasi | Anti-enumeration | Menyembunyikan apakah sebuah username terdaftar, dengan memakai pesan galat yang sama untuk semua jenis kegagalan |

## 4. Jejak Audit

| Istilah | Inggris | Arti |
|---|---|---|
| Jejak audit | Audit log | Catatan perubahan penting, disimpan di tabel `audit_logs` |
| Pelaku | Actor | Pengguna yang melakukan perubahan, diambil dari klaim `sub` |
| Target | Target | Objek yang diubah, misalnya id pengguna yang diperbarui |
| Kode aksi | Action code | Nama baku jenis kejadian, misalnya `USER_CREATED` atau `LOGIN_FAILED` |
| Transaksi terpisah | Separate transaction | Pencatatan jejak audit yang jalan di transaksi sendiri, supaya tetap tersimpan walau transaksi utama dibatalkan |
| Penolakan akses | Access denied | Jejak audit `ACCESS_DENIED` yang ditulis ketika identitas sah mencoba aksi di luar wewenangnya (403) |
| Rantai hash | Hash chain | Cara mengikat setiap baris audit ke baris sebelumnya lewat hash, sehingga perubahan atau penghapusan baris bisa dideteksi |
| Hash pendahulu | Previous hash | Nilai `prev_hash`, yaitu hash baris sebelumnya. Kosong hanya untuk baris pertama |
| Hash entri | Entry hash | Nilai `entry_hash`, yaitu hash baris itu sendiri, dihitung dari `prev_hash` dan seluruh isi baris |
| Verifikasi rantai | Chain verification | Pemeriksaan ulang seluruh hash dan keterkaitannya, dilaporkan sebagai valid atau rusak beserta alasannya |

## 5. Data dan Transaksi

| Istilah | Inggris | Arti |
|---|---|---|
| Skema | Schema | Bentuk tabel dan kolom, dikelola lewat migrasi |
| Migrasi | Migration | Berkas SQL yang mengubah skema, dijalankan berurutan oleh Flyway |
| Checksum | Checksum | Sidik jari isi migrasi. Kalau berubah, Flyway menolak jalan karena migrasi lama sudah pernah dipakai |
| Di luar urutan | Out of order | Migrasi dengan nomor versi lebih besar yang dijalankan sebelum migrasi bernomor lebih kecil |
| Penguncian optimistis | Optimistic locking | Menolak perubahan kalau data sudah berubah sejak dibaca, ditandai lewat kolom `version` |
| Versi | Version | Angka pada kolom `version`, dikirim sebagai field `version` di body permintaan saat mengubah data. Layanan ini tidak memakai header `If-Match` |
| Konflik versi | Version conflict | Situasi saat penguncian optimistis menolak perubahan, kode galat 409 |
| Halaman | Page | Potongan data yang dikembalikan sekaligus, punya nomor halaman dan jumlah baris per halaman |
| Batas ukuran halaman | Max page size | Batas keras jumlah baris per halaman, 50 di layanan ini |

## 6. Arsitektur

| Istilah | Inggris | Arti |
|---|---|---|
| Lapisan | Layer | Bagian perangkat lunak dengan tanggung jawab satu, ada domain, application, infrastructure, dan adapter |
| Port | Port | Antarmuka yang dideklarasikan domain untuk akses data, diimplementasikan di infrastruktur |
| Adapter | Adapter | Kelas yang menerjemahkan antara domain dan framework |
| Use case | Use case | Satu alur bisnis di lapisan application, misalnya login atau ganti kata sandi |
| Controller | Controller | Kelas di lapisan adapter yang menerima permintaan HTTP dan meneruskannya ke use case |
| DTO | Data Transfer Object | Objek khusus untuk bentuk permintaan dan respons di batas HTTP |
| Aturan arah | Dependency rule | Aturan bahwa domain tidak boleh mengimpor framework, diperiksa otomatis oleh `ArchitectureLayerTest` |
| Galat domain | Domain exception | Galat yang lahir dari aturan bisnis, dengan alasan dan kategori |

## 7. Keamanan

| Istilah | Inggris | Arti |
|---|---|---|
| Rate limit | Rate limit | Pembatas jumlah permintaan dari satu sumber dalam rentang waktu tertentu |
| Jendela waktu | Time window | Rentang waktu yang dipakai menghitung rate limit |
| Token pembawa | Bearer token | Token yang dipakai dengan sendirinya tanpa bukti kepemilikan tambahan |
| CORS | Cross-Origin Resource Sharing | Aturan apakah situs lain boleh memanggil API ini |
| CSRF | Cross-Site Request Forgery | Serangan yang memakai sesi browser korban untuk mengirim permintaan |
| CSP | Content Security Policy | Header yang mengatur sumber skrip, gaya, dan gambar yang boleh dimuat |
| HSTS | HTTP Strict Transport Security | Header yang memaksa peramban memakai HTTPS |
| Sumber tepercaya | Allowlist | Daftar origin yang diizinkan, bukan daftar blacklist |

## 8. Operasional

| Istilah | Inggris | Arti |
|---|---|---|
| Profil | Profile | Kumpulan konfigurasi untuk lingkungan tertentu, ada `dev` dan `prod` |
| Fail fast | Fail fast | Aplikasi menolak start daripada jalan dengan konfigurasi tidak lengkap |
| Health check | Health check | Endpoint yang memberi tahu apakah layanan masih hidup |
| Metrik | Metrics | Angka runtime yang dikumpulkan aplikasi, dibaca lewat Spring Boot Actuator |
| Tracing | Tracing | Pencatatan perjalanan satu permintaan lintas komponen, terdiri atas beberapa rentang |
| Rentang | Span | Satu unit kerja di dalam tracing, punya `spanId` dan terhubung ke satu `traceId` |
| Sampling | Sampling | Peluang sebuah permintaan dibuatkan rentang. Bawaan layanan ini `0.0`, artinya mati |
| Testcontainers | - | Pustaka yang menjalankan container Docker dari dalam pengujian, dipakai untuk database PostgreSQL terisolasi |
| Image container | Container image | Bungkusan aplikasi beserta runtime-nya, dibangun dari `Dockerfile` |
| Gate | Gate | Syarat yang harus lolos sebelum hasil dianggap sah, misalnya ambang cakupan 80 persen |
| Artefak | Artifact | Berkas hasil build yang diunggah, misalnya laporan cakupan dan berkas jar |
