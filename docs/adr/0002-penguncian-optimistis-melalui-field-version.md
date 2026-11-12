# ADR 0002 — Penguncian Optimistis Melalui Field Version

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Service ini menyimpan data yang bisa diubah oleh lebih dari satu pihak pada saat yang
sama. Contoh paling nyata: dua admin membuka halaman profil pengguna yang sama, lalu
satu sama sama menyalin. Keduanya menekan tombol simpan.

Tanpa penanganan, skenario itu berakhir dengan penimpaan diam-diam. Orang kedua
mengira perubahan pertamanya tersimpan, padahal sudah ditimpa. Untuk data peran
dan status aktif, ini berimplikasi serius: perubahan role bisa hilang tanpa
jejak.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Penguncian pessimis** (baris dikunci selama transaksi) | Tidak ada konflik sama sekali | Menghambat, bisa deadlock, tidak cocok untuk data yang jarang diubah bersamaan |
| **B. Penguncian optimistis lewat field version** | Ringan, konflik terdeteksi saat commit | Perlu penanganan konflik di sisi pemanggil |
| **C. Optimistis tanpa version, dengan penulis terakhir menang** | Paling sederhana | Penimpaan diam-diam, tidak ada deteksi |
| **D. Pesan dengan urutan waktu** | Cukup mudah | Presisi jam antar server bisa berbeda, masih ada celah |

## Keputusan

**Menggunakan penguncian optimistis lewat kolom `version` pada tabel `users`.**

Mekanismenya dua lapis:

1. Kolom `version` bertambah satu setiap kali baris diperbarui. Pertanyaan
   pembaruan menyertakan nilai version lama:
   `UPDATE users SET ... WHERE id = ? AND version = ?`. Kalau baris sudah diubah
   orang lain, jumlah baris yang terpengaruh nol, dan Hibernate melempar
   `ObjectOptimisticLockingFailureException`.
2. Before check pada level use case. Pemanggil mengirim versi yang dia baca
   sebelumnya. Kalau versi yang dikirim berbeda dengan versi di database, galat
   dilempatkan **sebelum** perubahan apa pun dilakukan, sehingga tidak ada
   penulisan separuh.

Respons untuk klien adalah `409 Conflict` dengan pesan "The resource was modified by
someone else. Reload and try again".

## Dampak

**Positif**

- Tidak ada perubahan yang hilang tanpa Pemberitahuan.
- Tidak ada lock database, jadi concurrency tinggi tidak mengunci tabel.
- Klien dapat mencoba lagi dengan data terbaru.

**Negatif**

- Klien wajib menyimpan dan mengirimkan versi. Field `version` karena itu ada di
  setiap DTO yang dapat diubah.
- Penulis kode harus secara sadar memanggil `saveAndFlush()` pada operasi yang
  perlu memaksa pemeriksaan versi, bukan mengandalkan flush di akhir transaksi.

**Alternatif yang ditolak**

- **A** ditolak karena risiko deadlock dan menurunkan throughput, padahal konflik
  pada data ini jarang dan secara alami bisa ditangani di sisi pemanggil.
- **C** ditolak karena justru menjadi masalah yang ingin diselesaikan.
- **D** ditolak karena presisi jam antar server tidak dapat dijamin, dan `version`
  justru lebih sederhana.

## Catatan tambahan

Penguncian optimistis ini terverifikasi lewat pengujian konkurensi
(`UserConcurrencyTest`): dua PATCH bersamaan atas satu data, hanya satu yang
berhasil, yang lain menerima galat konflik.
