# 0011 - Deteksi Pemakaian Ulang Token Refresh

- Status: diterima
- Tanggal: 2026-11-12

## Konteks

Rotasi token refresh membuat setiap token hanya bisa dipakai sekali. Token lama
dicabut saat ditukar, dan token baru diterbitkan untuk menggantikannya. Aturan
itu sudah berjalan, tetapi layanan tidak membedakan tiga keadaan yang berbeda:

1. token tidak pernah dikenal,
2. token dikenal tetapi sudah kedaluwarsa,
3. token dikenal, sudah dicabut, dan belum kedaluwarsa.

Keadaan ketiga adalah bukti paling kuat bahwa token dipakai oleh dua pihak.
Kalau diabaikan, pencuri yang berhasil menukar token lebih dulu bisa memakai
token hasil rotasi selama tujuh hari, sementara pemilik sah hanya melihat
tokennya ditolak dan harus login ulang. Tidak ada sinyal apa pun bagi operator.

Tabel `refresh_tokens` sudah menyimpan `revoked_at`, jadi buktinya sebenarnya
sudah ada. Yang kurang adalah aturan yang membacanya.

## Alternatif yang Dipertimbangkan

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Abaikan pemakaian ulang** | Paling sederhana, tidak ada perubahan perilaku | Pencurian token tidak terdeteksi dan pencuri bisa melanjutkan sesi |
| **B. Tolak dan catat audit saja** | Pengguna tidak terganggu, ada jejak untuk operator | Pencuri yang memegang token terbaru tetap bisa memakai sesinya |
| **C. Cabut seluruh sesi dan catat audit** | Pencuri kehilangan akses, jejaknya terlihat | Pemilik sah ikut ter-logout, termasuk saat klien mengulang permintaan karena respons jaringan hilang |
| **D. Cabut seluruh sesi dengan tenggang waktu** | Percobaan ulang jaringan tidak dianggap pencurian | Pencuri punya jendela waktu singkat untuk memakai token hasil rotasi |

## Keputusan

**Memilih C: token yang sudah dicabut tetapi belum kedaluwarsa lalu dipakai
lagi dianggap bukti pemakaian ulang.**

Aturan yang berlaku:

- Seluruh token refresh aktif milik pengguna dicabut, bukan hanya token yang
  dipakai ulang.
- Pencabutan dan catatan audit berjalan di **transaksi terpisah**
  (`REQUIRES_NEW`), karena permintaan refresh tetap dijawab dengan galat 401
  dan transaksi utamanya dibatalkan.
- Kejadian dicatat sebagai `REFRESH_TOKEN_REUSE` pada jejak audit, lengkap
  dengan jumlah token yang ikut dicabut.
- Respons tetap `401 Invalid refresh token`, sama seperti token yang tidak
  dikenal. Pemanggil tidak boleh bisa membedakan ketiga keadaan di atas.
- Token yang sudah kedaluwarsa tidak dianggap bukti pemakaian ulang. Itu
  keadaan biasa, misalnya saat klien lama mencoba sebelum sempat login ulang.
- Pencabutan massal memakai satu `UPDATE` yang mengisi `revoked_at`. Baris
  tidak dihapus, supaya bukti tetap ada sampai pembersihan berkala membuang
  token yang sudah kedaluwarsa.

## Konsekuensi

**Positif**

- Pencuri kehilangan akses, termasuk bila dia sudah memegang token hasil
  rotasi.
- Operator mendapat sinyal yang bisa dicari di jejak audit.
- Tidak ada kebocoran informasi: pesan galatnya sama untuk semua kegagalan.
- Satu `UPDATE` massal menjaga biaya pencabutan tetap datar, berapa pun jumlah
  sesi pengguna.

**Negatif**

- Klien yang kehilangan respons lalu mengulang permintaan dengan token lama
  akan mematikan seluruh sesinya sendiri. Ini kompromi yang disengaja; tanpa
  tenggang waktu, deteksinya tetap tegas.
- Pemilik sah tidak diberi tahu secara proaktif. Sinyalnya baru terlihat oleh
  operator lewat jejak audit.
- Setiap permintaan dengan token yang sudah dicabut menghasilkan satu catatan
  audit. Pengiriman berulang bisa menambah baris, tetapi tidak menambah akses.

## Alternatif yang Ditolak

- **A** ditolak karena membiarkan pencurian tidak terlihat sama sekali.
- **B** ditolak karena tidak menghentikan pencuri yang sudah memegang token
  terbaru. Jejak audit tanpa tindakan hanya memindahkan masalah ke operator.
- **D** ditolak karena menambah satu parameter konfigurasi dan jendela waktu
  yang harus dijelaskan ke operator, sementara manfaatnya hanya untuk kasus
  percobaan ulang yang jarang. Kalau nanti terbukti mengganggu, tenggang waktu
  bisa ditambahkan tanpa mengubah skema.
