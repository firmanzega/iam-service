# ADR 0004 — Menyimpan Hash Token Refresh, Bukan Token Mentah

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Token refresh harus bisa diverifikasi server: saat klien mengirim token refresh,
server perlu tahu apakah token itu benar milik sistem dan belum dicabut.

Cara paling langsung adalah menyimpan tokennya apa adanya di database, lalu
mencocokkan. Itu mudah, tapi menyimpan rahasia dalam bentuk yang bisa langsung
dipakai adalah risiko yang tidak perlu diambil.

Database adalah target paling umum dalam kebocoran data. Kalau token refresh
tersimpan mentah, satu-satunya hal yang memisahkan penyerang dari akses penuh ke
semua akun adalah satu query SELECT.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Simpan token mentah** | Sederhana, perbandingan cepat | Kebocoran database langsung berarti kebocoran token |
| **B. Simpan hash SHA-256** | Token tidak bisa dipakai langsung dari dump database | Perbandingan tetap cepat, biayanya kecil |
| **C. Simpan hash bcrypt** | Untuk password, ketahanan tinggi | Terlalu lambat untuk lookup pada setiap permintaan, dan tidak diperlukan |
| **D. Enkripsi, bukan hash** | Bisa dikembalikan ke bentuk semula | Kalau kunci bocor, semua token bocor |

## Keputusan

**Menyimpan SHA-256 dari token refresh, bukan tokennya sendiri.**

Alasannya:

1. Token refresh dibuat secara acak dengan entropi tinggi oleh server, bukan
   dipilih oleh manusia seperti kata sandi. Artinya tidak ada kamus yang perlu
   dicoba. Untuk nilai acak seperti itu, fungsi hash cepat seperti SHA-256 sudah
   cukup.
2. Kata sandi tetap memakai bcrypt, karena password manusia memang perlu
   ketahanan tinggi terhadap brute force. Dua kasus ini memang berbeda dan
   diperlakukan berbeda.
3. Pencocokan dilakukan lewat perbandingan langsung pada kolom unik
   `token_hash`, sehingga pencarian tetap memakai indeks.

Kolom `token_hash` memiliki batasan unik. Ini penting dan punya konsekuensi yang
tidak terlihat: kalau dua token refresh terbit pada detik yang sama untuk pengguna
yang sama, keduanya akan identik byte per byte karena claim `iat` dan `exp` punya
resolusi detik. Perbaikan: setiap token refresh diberi claim `jti` berisi UUID
acak, sehingga isinya pasti berbeda.

## Dampak

**Positif**

- Dump database tidak langsung memberi penyerang token yang bisa dipakai.
- Pencabutan tetap bisa dilakukan, karena status pencabutan disimpan terpisah
  dari hash-nya.
- Kolom unik pada `token_hash` sekaligus mencegah token yang sama dipakai dua kali.

**Negatif**

- Kalau ada bug di lapisan pencabutan, server tidak bisa menampilkan token
  aslinya untuk dipulihkan. Ini kompromi yang disengaja: lebih baik tidak bisa
  dipulihkan daripada bisa dibaca.
- Token yang sudah dicabut masih ada di tabel sampai dihapus. Perlu pembersihan
  berkala untuk baris yang sudah kedaluwarsa.

**Alternatif yang ditolak**

- **A** ditolak karena berhasil menghilangkan satu lapisan risiko
  kebocoran data.
- **C** ditolak karena bcrypt memang lambat. Hash yang lambat itu memang
 pilihan untuk alasan yang salah: tujuannya memperlambat penyerang yang
  mencoba banyak password, sementara di sini tidak ada tebakan yang dilakukan.
- **D** ditolak karena kunci enkripsi adalah satu titik kegagalan. Kalau kunci
  bocor, seluruh token bisa dibaca sekaligus, sedangkan hash tidak pernah bisa
  dibalik.
