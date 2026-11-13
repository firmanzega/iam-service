# ADR 0003 — Token JWT dengan Rotasi Token Refresh

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Sebelum keputusan ini, layanan ini sama sekali tidak punya autentikasi. Setiap
endpoint bisa dipanggil siapa saja. Untuk layanan yang mengelola identitas dan
peran, itu jelas tidak bisa dibiarkan.

Kebutuhan yang muncul:

1. Pemanggil harus bisa membuktikan identitasnya tanpa mengirim kata sandi pada
   setiap permintaan.
2. Akses harus bisa dibatasi berdasarkan peran.
3. Sesi yang sudah berjalan harus bisa dicabut, misalnya ketika kata sandi
   diganti atau akun dinonaktifkan.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Sesi di server (cookie)** | Sederhana, pencabutan mudah | Butuh penyimpanan state, skala horizontalnya mahal |
| **B. Token JWT tanpa refresh** | Paling sederhana | Masa berlaku token access jadi tradeoff: pendek berarti sering login, panjang berarti token yang dicuri tetap berlaku lama |
| **C. Token JWT access + refresh dengan rotasi** | Pencabusan mungkin, access token bisa pendek | Butuh penyimpanan token refresh dan logika rotasi |
| **D. Token opaque + tabel penyimpanan** | Pencabutan mudah, isi token tidak terbaca | Butuh lookup ke database pada setiap permintaan |
| **E. Token JWT tanpa tanda tangan, disimpan di database** | Mirip D | Sama seperti D, tapi boros |

## Keputusan

**Menggunakan token JWT bertanda tangan HS256, terdiri atas dua jenis:**

| Jenis | Masa berlaku | Isi | Disimpan di server |
|---|---|---|---|
| Access token | 15 menit | id pengguna, username, daftar role | Tidak |
| Refresh token | 7 hari | id pengguna, jenis token, id unik | Ya, hanya hash-nya |

Aturan yang berlaku:

- Access token dikirim pada header `Authorization: Bearer <token>`.
- Refresh token hanya dipakai pada `/v1/auth/refresh`, dan **langsung dicabut** setelah
  dipakai. Pemegang token refresh hanya boleh memakainya sekali. Inilah rotasi token.
- Token yang sudah dicabut lalu dipakai lagi dianggap bukti pencurian. Seluruh
  sesi pengguna dicabut, dan kejadiannya dicatat. Lihat
  [ADR 0011](0011-deteksi-pemakaian-ulang-token-refresh.md).
- Setiap perubahan yang menyentuh sesi pengguna (ganti kata sandi, ganti role,
  nonaktifkan akun) **mencabut seluruh token refresh** pengguna tersebut.
- Endpoint yang butuh autentikasi mengembalikan `401` bila token tidak ada atau
  tidak valid, dan `403` bila token valid tetapi peran tidak cukup.

## Dampak

**Positif**

- Access token bisa berumur pendek tanpa membuat pengguna sering login.
- Sesi bisa dicabut seketika karena status ada di server, bukan di dalam token.
- Tidak ada penyimpanan state untuk sesi, jadi menambah instance layanan mudah.
- Klien cukup membaca daftar role dari token untuk menampilkan tombol sesuai peran.

**Negatif**

- Pencabutan tidak langsung berlaku pada access token yang sudah terbit. Role
  yang dicabut masih terbawa sampai access token-nya kedaluwarsa. Ini bisa diterima
  karena masa hidup access token hanya 15 menit, dan pencabutan token refresh
  memaksa pengguna login ulang.
- Memerlukan kode penanda tangan yang harus dijaga. Kalau bocor, penyerang bisa
  membuat token sendiri.
- Klaim role di dalam token bisa basi. Ini bisa diterima dengan alasan yang sama.

**Alternatif yang ditolak**

- **A** ditolak karena menambah state server dan membuat diskalakan horizontal
  lebih repot.
- **B** ditolak karena memaksa memilih salah satu dari dua hal: access token terlalu
  pendek (sering login) atau terlalu panjang (risiko penyalahgunaan).
- **D** dan **E** ditolak karena setiap permintaan harus bertanya ke database.
  Untuk endpoint yang sering dipanggil, itu beban yang tidak perlu.
